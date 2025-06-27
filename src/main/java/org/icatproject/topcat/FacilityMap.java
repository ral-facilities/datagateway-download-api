package org.icatproject.topcat;

import java.util.HashMap;
import java.util.Map;

import org.icatproject.topcat.exceptions.InternalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FacilityMap {
    public static class Facility {
		public String icatUrl;
        public String idsUrl;
		public Long countLimit = null;
		public Long sizeLimit = null;
    }
	
    private static FacilityMap instance = null;

    public synchronized static FacilityMap getInstance() throws InternalException {
       if(instance == null) {
          instance = new FacilityMap();
       }
       return instance;
    }
    
	private Logger logger = LoggerFactory.getLogger(FacilityMap.class);
	
	private Properties properties;
	private Map<String, Facility> facilityMapping = new HashMap<>();
	
	public FacilityMap() throws InternalException{
		// The "normal" case: use the Topcat Properties instance (that reads run.properties)
		this(Properties.getInstance());
	}

	public FacilityMap(Properties injectedProperties) throws InternalException{
		
		// This allows us to inject a mock Properties instance for testing
		
		properties = injectedProperties;
		
		logger.info("FacilityMap: facility.list = '" + properties.getProperty("facility.list","") + "'");
		
		String[] facilities = properties.getProperty("facility.list","").split("([ ]*,[ ]*|[ ]+)");
		
		// Complain/log if property is not set
		if( facilities.length == 0 || (facilities.length == 1 && facilities[0].length() == 0)){
			logger.error( "FacilityMap: property facility.list is not defined.");
			throw new InternalException("Property facility.list is not defined.");
		}
		
		for( String facilityName : facilities ){
			logger.info("FacilityMap: looking for properties for facility '" + facilityName + "'...");
			Facility facility = new Facility();
			String icatUrl = properties.getProperty("facility." + facilityName + ".icatUrl","");
			// Complain/log if property is not set
			if( icatUrl.length() == 0 ){
				String error = "FacilityMap: property facility." + facilityName + ".icatUrl is not defined.";
				logger.error( error );
				throw new InternalException( error );
			}
			logger.info("FacilityMap: icatUrl for facility '" + facilityName + "' is '" + icatUrl + "'");
			facility.icatUrl = icatUrl;

			String idsUrl = properties.getProperty("facility." + facilityName + ".idsUrl","");
			// Complain/log if property is not set
			if( idsUrl.length() == 0 ){
				String error = "FacilityMap: property facility." + facilityName + ".idsUrl is not defined.";
				logger.error( error );
				throw new InternalException( error );
			}
			logger.info("FacilityMap: idsUrl for facility '" + facilityName + "' is '" + idsUrl + "'");
			facility.idsUrl = idsUrl;

			String countString = properties.getProperty("facility." + facilityName + ".limit.count");
			if (countString != null) {
				facility.countLimit = Long.valueOf(countString);
			}

			String sizeString = properties.getProperty("facility." + facilityName + ".limit.size");
			if (sizeString != null) {
				facility.sizeLimit = Long.valueOf(sizeString);
			}

			facilityMapping.put(facilityName, facility);
		}
	}

	public String validateFacilityName(String facility) throws InternalException {
		if (facility == null) {
			String defaultFacilityName = properties.getProperty("defaultFacilityName");
			if (defaultFacilityName == null) {
				String error = "FacilityMap.validateFacilityName: facility is null and no default set in config";
				logger.error( error );
				throw new InternalException( error );
			}
			facility = defaultFacilityName;
		}
		return facility;
	}
	
	public String getIcatUrl(String facilityName) throws InternalException {
		Facility facility = getFacility(facilityName);
		return facility.icatUrl;
	}

	public String getIdsUrl(String facilityName) throws InternalException {
		Facility facility = getFacility(facilityName);
		return facility.idsUrl;
	}
	
	public String getDownloadUrl( String facility, String downloadType ) throws InternalException{
		facility = validateFacilityName(facility);
		String url = "";
		// First, look for the property directly
		url = properties.getProperty( "facility." + facility + ".downloadType." + downloadType, "" );
		if( url.length() == 0 ){
			// No such property, so fall back to the facility idsUrl
			logger.trace("FacilityMap.getDownloadUrl: no specific property for facility '" 
					+ facility + "' and download type '" + downloadType + "'; returning idsUrl instead" );
			url = this.getIdsUrl(facility);
		}
		return url;
	}

	/**
	 * @param facilityName ICAT Facility.name
	 * @return Limit on the number of Datafiles allowed in a cart, or null if not limit set
	 * @throws InternalException if facilityName is not a key in facilityMapping
	 */
	public Long getCountLimit(String facilityName) throws InternalException {
		Facility facility = getFacility(facilityName);
		return facility.countLimit;
	}

	/**
	 * @param facilityName ICAT Facility.name
	 * @return Limit on the total size of Datafiles allowed in a cart, or null if not limit set
	 * @throws InternalException if facilityName is not a key in facilityMapping
	 */
	public Long getSizeLimit(String facilityName) throws InternalException {
		Facility facility = getFacility(facilityName);
		return facility.sizeLimit;
	}

	/**
	 * @param facilityName ICAT Facility.name
	 * @return Facility config object with the given name
	 * @throws InternalException if facilityName is not a key in facilityMapping
	 */
	private Facility getFacility(String facilityName) throws InternalException {
		facilityName = validateFacilityName(facilityName);
		Facility facility = facilityMapping.get(facilityName);
		if (facility == null) {
			String error = "FacilityMap.getFacility: unknown facility: " + facility;
			logger.error(error);
			throw new InternalException(error);
		}
		return facility;
	}
}
