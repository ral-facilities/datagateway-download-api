package org.icatproject.topcat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.icatproject.topcat.exceptions.ForbiddenException;
import org.icatproject.topcat.exceptions.InternalException;
import org.icatproject.topcat.exceptions.TopcatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for tracking which transport mechanisms at a given facility are restricted to
 * certain authentication mechanisms (identified by the userName prefix) or Groupings of
 * Users.
 */
public class TransportMap {

    public static class TransportMechanism {
        public String idsUrl;
        public String displayName;
        public String description;
        public final Set<String> disallowedAuthn = new HashSet<>();
        public final Set<String> allowedGroupings = new HashSet<>();
    }

    private static TransportMap instance = null;

    public synchronized static TransportMap getInstance() throws InternalException {
        if (instance == null) {
            instance = new TransportMap();
        }
        return instance;
    }

    private Logger logger = LoggerFactory.getLogger(PriorityMap.class);
    private HashMap<String, HashMap<String, TransportMechanism>> mapping = new HashMap<>();

    /**
     * Initialise the map from the properties file.
     * @throws InternalException if FacilityMap cannot return the idsUrl
     */
    public TransportMap() throws InternalException {
        Properties properties = Properties.getInstance();
        FacilityMap facilityMap = FacilityMap.getInstance();
        Set<String> facilitySet =  facilityMap.getFacilities();
        for (String facilityName : facilitySet) {
            HashMap<String, TransportMechanism> facilityMapping = new HashMap<>();
            String downloadTypeList = properties.getProperty("facility." + facilityName + ".downloadType.list", "");
            downloadTypeList = downloadTypeList.strip();
            if (downloadTypeList.equals("")) {
                continue;
            }
            for (String downloadType: downloadTypeList.split("([ ]*,[ ]*|[ ]+)")) {
                TransportMechanism transportMechanism = new TransportMechanism();
                String propertyRoot = "facility." + facilityName + ".downloadType." + downloadType;
                transportMechanism.idsUrl = facilityMap.getDownloadUrl(facilityName, downloadType);
                transportMechanism.displayName = properties.getProperty(propertyRoot + ".displayName", "");
                transportMechanism.description = properties.getProperty(propertyRoot + ".description", "");

                String allowedGroupings = properties.getProperty(propertyRoot + ".allowedGroupings", "");
                allowedGroupings = allowedGroupings.strip();
                if (!allowedGroupings.equals("")) {
                    for (String allowedGrouping : allowedGroupings.split("([ ]*,[ ]*|[ ]+)")) {
                        transportMechanism.allowedGroupings.add(allowedGrouping);
                    }
                }

                String disallowedAuthenticators = properties.getProperty(propertyRoot + ".disallowedAuthn", "");
                disallowedAuthenticators = disallowedAuthenticators.strip();
                if (!disallowedAuthenticators.equals("")) {
                    for (String disallowedAuthn : disallowedAuthenticators.split("([ ]*,[ ]*|[ ]+)")) {
                        transportMechanism.disallowedAuthn.add(disallowedAuthn);
                    }
                }

                facilityMapping.put(downloadType, transportMechanism);
            }
            mapping.put(facilityName, facilityMapping);
        }
    }

    /**
     * Checks if the specified facility, transport and userName are allowed.
     * 
     * @param facility  ICAT Facility.name
     * @param transport Transport mechanism String, such as globus or http(s)
     * @param userName  String of the form prefix/user
     * @throws TopcatException if access is forbidden or an unexpected error occurs
     */
    public void checkAllowed(String facility, String transport, String userName, IcatClient icatClient) throws TopcatException {
        if (!isAllowed(facility, transport, userName, icatClient)) {
            throw new ForbiddenException(userName + " not allowed to use " + transport);
        }
    }

    /**
     * Checks if the specified facility, transport and userName are allowed.
     * 
     * @param facility   ICAT Facility.name
     * @param transport  Transport mechanism String, such as globus or http(s)
     * @param userName   String in the form prefix/user
     * @param icatClient IcatClient used to query for group membership
     * @throws TopcatException 
     */
    public boolean isAllowed(String facility, String transport, String userName, IcatClient icatClient) throws TopcatException {
        TransportMechanism transportMechanism = getTransportMechanism(facility, transport);
        if (transportMechanism == null) {
            logger.debug("No explicit transport mapping found for {}.{}, all users allowed", facility, transport);
            return true;
        }

        if (!transportMechanism.allowedGroupings.isEmpty()) {
            if (icatClient.isInGroups(userName, transportMechanism.allowedGroupings)) {
                logger.debug("{} allowed due to group membership", transport);
                return true;
            } else {
                logger.warn("{} not allowed due to lack of group membership", transport);
                return false;
            }
        } else if (!transportMechanism.disallowedAuthn.isEmpty()) {
            int index = userName.indexOf("/");
            String prefix = "";
            if (index > 0) {
                prefix = userName.substring(0, index);
                if (transportMechanism.disallowedAuthn.contains(prefix)) {
                    logger.warn("{} not allowed for authentication mechanism {}", transport, prefix);
                    return false;
                }
            }
            logger.debug("{} allowed for authentication mechanism {}", transport, prefix);
            return true;
        } else {
            logger.debug("No restrictions for {}", transport);
            return true;
        }
    }

    /**
     * 
     * @param facilityName ICAT Facility.name
     * @param transport    Name of the DownloadType, AKA TransportMechanism
     * @return TransportMechanism Object holding the idsUrl, displayName and description
     */
    public TransportMechanism getTransportMechanism(String facilityName, String transport) {
        HashMap<String, TransportMechanism> facilityMapping = mapping.get(facilityName);
        if (facilityMapping == null) {
            return null;
        }
        return facilityMapping.get(transport);
    }
}
