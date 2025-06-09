package org.icatproject.topcat;

import java.io.ByteArrayInputStream;
import java.util.HashMap;

import org.icatproject.topcat.exceptions.ForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

/**
 * Class for tracking which transport mechanisms at a given facility are restricted to
 * certain authentication mechanisms (identified by the userName prefix).
 */
public class TransportMap {

    private static TransportMap instance = null;

    public synchronized static TransportMap getInstance() {
        if (instance == null) {
            instance = new TransportMap();
        }
        return instance;
    }

    private Logger logger = LoggerFactory.getLogger(PriorityMap.class);
    private HashMap<String, HashMap<String, HashMap<String, Boolean>>> mapping = new HashMap<>();

    /**
     * Initialise the map from the properties file.
     */
    public TransportMap() {
        Properties properties = Properties.getInstance();
        String transportAllowed = properties.getProperty("transportAllowed", "{}");
        JsonReader reader = Json.createReader(new ByteArrayInputStream(transportAllowed.getBytes()));
        JsonObject object = reader.readObject();
        for (String facility : object.keySet()) {
            HashMap<String, HashMap<String, Boolean>> facilityMapping = new HashMap<>();
            JsonObject facilityObject = object.getJsonObject(facility);
            for (String transport: facilityObject.keySet()) {
                HashMap<String, Boolean> transportMapping = new HashMap<>();
                JsonObject transportObject = facilityObject.getJsonObject(transport);
                for (String authenticationMechanism : transportObject.keySet()) {
                    transportMapping.put(authenticationMechanism, transportObject.getBoolean(authenticationMechanism));
                }
                facilityMapping.put(transport, transportMapping);
            }
            mapping.put(facility, facilityMapping);
        }
    }

    /**
     * Checks if the specified facility, transport and userName are allowed.
     * 
     * @param facility  ICAT Facility.name
     * @param transport Transport mechanism String, such as globus or http(s)
     * @param userName  String of the form prefix/user
     * @throws ForbiddenException if the prefix is specifically disallowed for the
     *                            facility and transport specified
     */
    public void checkAllowed(String facility, String transport, String userName) throws ForbiddenException {
        HashMap<String, HashMap<String, Boolean>> facilityMapping = mapping.get(facility);
        if (facilityMapping == null) {
            logger.debug("No explicit facility mapping found for {}, allowing", facility);
            return;
        }
        HashMap<String, Boolean> transportMapping = facilityMapping.get(transport);
        if (transportMapping == null) {
            logger.debug("No explicit transport mapping found for {}, allowing", transport);
            return;
        }
        int index = userName.indexOf("/");
        if (index < 0) {
            logger.debug("No explicit authentication mechanism for {}, allowing", userName);
            return;
        }
        String prefix = userName.substring(0, index);
        if (transportMapping.getOrDefault(prefix, true)) {
            logger.debug("{} allowed for authentication mechanism {}", transport, prefix);
            return;
        }
        logger.warn("{} not allowed for authentication mechanism {}", transport, prefix);
        throw new ForbiddenException(prefix + " users are not allowed to use " + transport);
    }
}
