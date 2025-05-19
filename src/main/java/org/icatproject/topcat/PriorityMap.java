package org.icatproject.topcat;

import java.io.ByteArrayInputStream;
import java.util.HashMap;

import org.icatproject.topcat.exceptions.ForbiddenException;
import org.icatproject.topcat.exceptions.InternalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

public class PriorityMap {

    private static PriorityMap instance = null;

    public synchronized static PriorityMap getInstance() throws InternalException {
        if (instance == null) {
            instance = new PriorityMap();
        }
        return instance;
    }

    String anonUserName;
    boolean anonDownloadEnabled;
    private int defaultPriority;
    private int authenticatedPriority;
    private HashMap<String, Integer> userMapping = new HashMap<>();
    private HashMap<Integer, String> mapping = new HashMap<>();
    private Logger logger = LoggerFactory.getLogger(PriorityMap.class);

    public PriorityMap() {
        Properties properties = Properties.getInstance();

        anonUserName = properties.getProperty("anonUserName", "");
        if (anonUserName.equals("")) {
            logger.warn("anonUserName not defined, cannot distinguish anonymous and authenticated users so "
                    + "authenticated priority will be used as default level");
        }
        anonDownloadEnabled = Boolean.parseBoolean(properties.getProperty("anonDownloadEnabled", "true"));
        String defaultString;
        if (anonDownloadEnabled) {
            defaultString = properties.getProperty("queue.priority.default", "0");
            defaultPriority = Integer.valueOf(defaultString);
        } else {
            defaultString = "0";
            defaultPriority = 0;
        }

        String authenticatedString = properties.getProperty("queue.priority.authenticated", defaultString);
        setAuthenticatedPriority(authenticatedString);

        String userString = properties.getProperty("queue.priority.user", "{}");
        JsonReader reader = Json.createReader(new ByteArrayInputStream(userString.getBytes()));
        JsonObject object = reader.readObject();
        for (String key : object.keySet()) {
            int priority = object.getInt(key);
            userMapping.put(key, priority);
        }

        String property = "queue.priority.investigationUser.default";
        String investigationUserString = properties.getProperty(property, authenticatedString);
        updateMapping(Integer.valueOf(investigationUserString), "user.investigationUsers IS NOT EMPTY");

        property = "queue.priority.instrumentScientist.default";
        String instrumentScientistString = properties.getProperty(property, authenticatedString);
        updateMapping(Integer.valueOf(instrumentScientistString), "user.instrumentScientists IS NOT EMPTY");

        String investigationUserProperty = properties.getProperty("queue.priority.investigationUser.roles");
        String investigationUserCondition = "EXISTS ( SELECT o FROM InvestigationUser o WHERE o.role='";
        parseObject(investigationUserProperty, investigationUserCondition);

        String instrumentScientistProperty = properties.getProperty("queue.priority.instrumentScientist.instruments");
        String instrumentScientistCondition = "EXISTS ( SELECT o FROM InstrumentScientist o WHERE o.instrument.name='";
        parseObject(instrumentScientistProperty, instrumentScientistCondition);

        String groupingProperty = properties.getProperty("queue.priority.grouping");
        String groupingCondition = "EXISTS ( SELECT o FROM UserGroup o WHERE o.grouping.name='";
        parseObject(groupingProperty, groupingCondition);
    }

    /**
     * @param userName ICAT userName
     * @throws ForbiddenException If userName is the anonUserName and anonDownloadEnabled is false
     */
    public void checkAnonDownloadEnabled(String userName) throws ForbiddenException {
		if (userName.equals(anonUserName) && !anonDownloadEnabled) {
			logger.warn("submitCart request denied for anonymous user");
			throw new ForbiddenException("Downloads by anonymous users not supported");
		}
    }

    /**
     * Set the minimum priority for all authenticated Users. This cannot be lower
     * than the defaultPriority, which will be used instead if this is the case.
     * 
     * @param authenticatedString The value read from the run.properties file
     */
    private void setAuthenticatedPriority(String authenticatedString) {
        authenticatedPriority = Integer.valueOf(authenticatedString);
        if (authenticatedPriority < 1 && defaultPriority >= 1) {
            String msg = "queue.priority.authenticated disabled with value " + authenticatedString;
            msg += " but queue.priority.default enabled with value " + defaultPriority;
            msg += "\nAuthenticated users will use default priority if no superseding priority applies";
            logger.warn(msg);
            authenticatedPriority = defaultPriority;
        } else if (authenticatedPriority >= 1 && authenticatedPriority > defaultPriority) {
            String msg = "queue.priority.authenticated enabled with value " + authenticatedString;
            msg += " but queue.priority.default supersedes with value " + defaultPriority;
            msg += "\nAuthenticated users will use default priority if no superseding priority applies";
            logger.warn(msg);
            authenticatedPriority = defaultPriority;
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
    private void parseObject(String propertyString, String conditionPrefix) {
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
    private void updateMapping(int priority, String newCondition) {
        if (priority < 1) {
            logger.warn("Non-positive priority found in mapping, ignoring entry");
            return;
        } else if (authenticatedPriority >= 1 && priority >= authenticatedPriority) {
            logger.warn("Priority set in mapping would be superseded by queue.priority.authenticated, ignoring entry");
            return;
        }

        String oldCondition = mapping.get(priority);
        if (oldCondition != null) {
            mapping.put(priority, oldCondition + " OR " + newCondition);
        } else {
            mapping.put(priority, newCondition);
        }
    }

    /**
     * @return Mapping of priority level to a JPQL condition which defines the Users
     *         who have this priority
     */
    public HashMap<Integer, String> getMapping() {
        return mapping;
    }

    /**
     * @return The priority which applies to this named user,
     *         or null if a specific priority is not defined
     */
    public Integer getUserPriority(String userName) {
        return userMapping.get(userName);
    }

    /**
     * @return The priority which applies to all authenticated users
     */
    public int getAuthenticatedPriority() {
        return authenticatedPriority;
    }

    /**
     * @return The priority which applies to all users, included anonymous access
     */
    public int getDefaultPriority() {
        return defaultPriority;
    }
}
