package org.icatproject.topcat;

import java.io.ByteArrayInputStream;
import java.util.HashMap;

import org.icatproject.topcat.exceptions.ForbiddenException;
import org.icatproject.topcat.exceptions.InternalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

public class PriorityMap {

    public enum EntityType {
        cart,
        dataCollection,
        visit,
        files,
    }

    class EntityPriorityMap {
        int defaultPriority;
        HashMap<String, Integer> authenticatedMapping = new HashMap<>();
        HashMap<String, Integer> userMapping = new HashMap<>();
        HashMap<Integer, String> queryMapping = new HashMap<>();

        /**
         * Extracts a String key to priority level mapping for any criteria (user, authn).
         * 
         * @param propertyString String representing a JsonObject from the run.properties
         *                       file, or {}
         * @param mapping        HashMap from String key to numeric priority level
         */
        void parseObject(String propertyString, HashMap<String, Integer> mapping) {
            try (JsonReader reader = Json.createReader(new ByteArrayInputStream(propertyString.getBytes()))) {
                JsonObject object = reader.readObject();
                for (String key : object.keySet()) {
                    int priority = object.getInt(key);
                    mapping.put(key, priority);
                }
            } catch (JsonException e) {
                logger.error("Could not parse {} as JSON object, defaulting to empty object", propertyString);
            }
        }

        /**
         * Extracts each key from a JsonObject, and appends this to the JPQL condition
         * for this priority level with OR.
         * 
         * @param propertyString  String representing a JsonObject from the
         *                        run.properties file, or null
         * @param conditionPrefix JPQL condition which will be formatted with each key
         *                        in the object
         */
        void parseObject(String propertyString, String conditionPrefix) {
            if (propertyString == null) {
                return;
            }
            JsonReader reader = Json.createReader(new ByteArrayInputStream(propertyString.getBytes()));
            JsonObject object = reader.readObject();
            for (String key : object.keySet()) {
                int priority = object.getInt(key);
                updateMapping(priority, conditionPrefix + key + "' AND o.user=user )");
            }
        }

        /**
         * Appends the newCondition to the mapping at the specified priority level using
         * OR.
         * 
         * @param priority     Priority of the new condition
         * @param newCondition Fully formatted JPQL condition
         */
        void updateMapping(int priority, String newCondition) {
            if (priority < 1) {
                logger.warn("Non-positive priority found in mapping, ignoring entry");
                return;
            } else if (defaultPriority >= 1 && priority >= defaultPriority) {
                logger.warn("Priority set in mapping would be superseded by queue.priority.default, ignoring entry");
                return;
            }

            String oldCondition = queryMapping.get(priority);
            if (oldCondition != null) {
                queryMapping.put(priority, oldCondition + " OR " + newCondition);
            } else {
                queryMapping.put(priority, newCondition);
            }
        }
    }

    static PriorityMap instance = null;

    public synchronized static PriorityMap getInstance() throws InternalException {
        if (instance == null) {
            instance = new PriorityMap();
        }
        return instance;
    }

    String anonUserName;
    boolean anonDownloadEnabled;
    HashMap<EntityType, EntityPriorityMap> entityPriorities = new HashMap<>();
    private Logger logger = LoggerFactory.getLogger(PriorityMap.class);

    public PriorityMap() {
        Properties properties = Properties.getInstance();

        anonUserName = properties.getProperty("anonUserName", "");
        if (anonUserName.equals("")) {
            logger.warn("anonUserName not defined, cannot distinguish anonymous and authenticated users so "
                    + "authenticated priority will be used as default level");
        }
        anonDownloadEnabled = Boolean.parseBoolean(properties.getProperty("anonDownloadEnabled", "true"));

        for (EntityType entityType : EntityType.values()) {
            EntityPriorityMap entityPriorityMap = new EntityPriorityMap();
            String prefix = "queue.priority." + entityType.toString();
            String defaultString = properties.getProperty(prefix + ".default", "0");
            entityPriorityMap.defaultPriority = Integer.valueOf(defaultString);

            String authenticatedString = properties.getProperty(prefix + ".authenticated", "{}");
            entityPriorityMap.parseObject(authenticatedString, entityPriorityMap.authenticatedMapping);

            String userString = properties.getProperty(prefix + ".user", "{}");
            entityPriorityMap.parseObject(userString, entityPriorityMap.userMapping);

            String property = prefix + ".investigationUser.default";
            String investigationUserString = properties.getProperty(property, defaultString);
            Integer priority = Integer.valueOf(investigationUserString);
            entityPriorityMap.updateMapping(priority, "user.investigationUsers IS NOT EMPTY");

            property = prefix + ".instrumentScientist.default";
            String instrumentScientistString = properties.getProperty(property, defaultString);
            priority = Integer.valueOf(instrumentScientistString);
            entityPriorityMap.updateMapping(priority, "user.instrumentScientists IS NOT EMPTY");

            String investigationUserProperty = properties.getProperty(prefix + ".investigationUser.roles");
            String investigationUserCondition = "EXISTS ( SELECT o FROM InvestigationUser o WHERE o.role='";
            entityPriorityMap.parseObject(investigationUserProperty, investigationUserCondition);

            String instrumentScientistProperty = properties.getProperty(prefix + ".instrumentScientist.instruments");
            String instrumentScientistCondition = "EXISTS ( SELECT o FROM InstrumentScientist o WHERE o.instrument.name='";
            entityPriorityMap.parseObject(instrumentScientistProperty, instrumentScientistCondition);

            String groupingProperty = properties.getProperty(prefix + ".grouping");
            String groupingCondition = "EXISTS ( SELECT o FROM UserGroup o WHERE o.grouping.name='";
            entityPriorityMap.parseObject(groupingProperty, groupingCondition);

            entityPriorities.put(entityType, entityPriorityMap);
        }
    }

    /**
     * @param userName ICAT userName
     * @throws ForbiddenException If userName is the anonUserName and
     *                            anonDownloadEnabled is false
     */
    public void checkAnonDownloadEnabled(String userName) throws ForbiddenException {
        if (userName.equals(anonUserName) && !anonDownloadEnabled) {
            logger.warn("submitCart request denied for anonymous user");
            throw new ForbiddenException("Downloads by anonymous users not supported");
        }
    }

    /**
     * @param entityType Type of entity that can be queued for Download.
     * @return Mapping of priority level to a JPQL condition which defines the Users
     *         who have that priority for entities of type entityType.
     */
    public HashMap<Integer, String> getQueryMapping(EntityType entityType) {
        return entityPriorities.get(entityType).queryMapping;
    }

    /**
     * @param entityType Type of entity that can be queued for Download.
     * @param userName   String in the format prefix/userName
     * @return The priority which applies to this named user for entities of type
     *         entityType, or null if a specific priority is not defined
     */
    public Integer getUserPriority(EntityType entityType, String userName) {
        return entityPriorities.get(entityType).userMapping.get(userName);
    }

    /**
     * @param entityType Type of entity that can be queued for Download.
     * @param userName   String in the format prefix/userName
     * @return Relevant priority for entities of type entityType if prefix present
     *         and recognised, otherwise defaultPriority
     */
    public Integer getAuthenticatedPriority(EntityType entityType, String userName) {
        int index = userName.indexOf("/");
        Integer defaultPriority = entityPriorities.get(entityType).defaultPriority;
        if (index < 0) {
            String format = "No explicit authentication mechanism for {}, using default priority {}";
            logger.debug(format, userName, defaultPriority);
            return defaultPriority;
        }
        String prefix = userName.substring(0, index);
        return entityPriorities.get(entityType).authenticatedMapping.getOrDefault(prefix, defaultPriority);
    }

    /**
     * @param entityType Type of entity that can be queued for Download.
     * @return The priority which applies to entities of this type for any user
     *         without a specific setting.
     */
    public int getDefaultPriority(EntityType entityType) {
        return entityPriorities.get(entityType).defaultPriority;
    }
}
