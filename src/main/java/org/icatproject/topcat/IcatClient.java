package org.icatproject.topcat;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.net.URLEncoder;

import org.icatproject.topcat.httpclient.*;
import org.icatproject.topcat.exceptions.*;
import org.apache.commons.lang3.StringUtils;
import org.icatproject.topcat.domain.*;

import jakarta.json.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IcatClient {

	private Logger logger = LoggerFactory.getLogger(IcatClient.class);

    private HttpClient httpClient;
    private String sessionId;
   
    public IcatClient(String url, String sessionId){
        this.httpClient = new HttpClient(url + "/icat");
        this.sessionId = sessionId;
    }

    public String getUserName() throws TopcatException {
    	try {
    		Response response = httpClient.get("session/" + sessionId, new HashMap<String, String>());
    		if(response.getCode() == 404){
                throw new NotFoundException("Could not run getUserName got a 404 response");
            } else if(response.getCode() >= 400){
                throw new BadRequestException(Utils.parseJsonObject(response.toString()).getString("message"));
            }
    		return Utils.parseJsonObject(response.toString()).getString("userName");
    	} catch (TopcatException e){
            throw e;
    	} catch (Exception e){
            throw new BadRequestException(e.getMessage());
    	}
    }

	public Boolean isAdmin() throws TopcatException {
		try {
			String[] adminUserNames = getAdminUserNames();
			String userName = getUserName();
			int i;

			for (i = 0; i < adminUserNames.length; i++) {
				if(userName.equals(adminUserNames[i])){
					return true;
				}
			}
		} catch(Exception e){
			logger.error("isAdmin: " + e.getMessage());
			// Ought to throw a BadRequestException here,
			// but existing usage expects a return value of false in this case.
			// throw new BadRequestException(e.getMessage());
		}
		return false;
	}

	public String getFullName() throws TopcatException {
		try {
			String query = "select user.fullName from User user where user.name = :user";
			String url = "entityManager?sessionId=" + URLEncoder.encode(sessionId, "UTF8") + "&query=" + URLEncoder.encode(query, "UTF8");
    		Response response = httpClient.get(url, new HashMap<String, String>());
    		
    		if(response.getCode() == 404){
    			logger.error("IcatClient.getFullName: got a 404 response");
                throw new NotFoundException("Could not run getFullName got a 404 response");
            } else if(response.getCode() >= 400){
            	String message = Utils.parseJsonObject(response.toString()).getString("message");
    			logger.error("IcatClient.getFullName: got a " + response.getCode() + " response: " + message);
                throw new BadRequestException(Utils.parseJsonObject(response.toString()).getString("message"));
            }

    		JsonArray responseArray = Utils.parseJsonArray(response.toString());
    		if( responseArray.size() == 0 || responseArray.isNull(0) ){
    			logger.warn("IcatClient.getFullName: client returned no or null result, so returning userName");
    			return getUserName();
    		} else {
    			return responseArray.getString(0);
    		}
    	} catch (TopcatException e){
            throw e;
    	} catch (Exception e){
            throw new BadRequestException(e.getMessage());
    	}
	}

	/**
	 * Get all Datasets whose parent Investigation has the specified visitId.
	 * 
	 * @param visitId ICAT Investigation.visitId
	 * @return JsonArray of Dataset fields, where each entry is a JsonArray of
	 *         [dataset.id, dataset.fileCount].
	 * @throws TopcatException
	 */
	public JsonArray getDatasets(String visitId) throws TopcatException {
			String query = "SELECT dataset.id, dataset.fileCount from Dataset dataset";
			query += " WHERE dataset.investigation.visitId = '" + visitId + "' ORDER BY dataset.id";
		return submitQuery(query);
	}

	/**
	 * Get all Datafiles in the list of file locations.
	 * 
	 * @param files List of ICAT Datafile.locations
	 * @return JsonArray of Datafile ids.
	 * @throws TopcatException
	 */
	public JsonArray getDatafiles(List<String> files) throws TopcatException {
		StringBuilder stringBuilder = new StringBuilder();
		ListIterator<String> fileIterator = files.listIterator();
		stringBuilder.append("'" + fileIterator.next() + "'");
		fileIterator.forEachRemaining(file -> {
			stringBuilder.append(",");
			stringBuilder.append("'" + file + "'");
		});
		String formattedFiles = stringBuilder.toString();
		String query = "SELECT datafile.id from Datafile datafile";
		query += " WHERE datafile.location in (" + formattedFiles + ") ORDER BY datafile.id";
		return submitQuery(query);
	}

	/**
	 * Utility method to get the fileCount (not size) of a Dataset by COUNT of its
	 * child Datafiles. Ideally the fileCount field should be used, this is a
	 * fallback option if that field is not set.
	 * 
	 * @param datasetId ICAT Dataset.id
	 * @return The number of Datafiles in the specified Dataset
	 * @throws TopcatException
	 */
	public long getDatasetFileCount(long datasetId) throws TopcatException {
			String query = "SELECT COUNT(datafile) FROM Datafile datafile WHERE datafile.dataset.id = " + datasetId;
		JsonArray jsonArray = submitQuery(query);
		return jsonArray.getJsonNumber(0).longValueExact();
	}

	/**
	 * Utility method for submitting an unformatted query to the entityManager
	 * endpoint, and returning the resultant JsonArray.
	 * 
	 * @param query Unformatted String query to submit
	 * @return JsonArray of results, contents will depend on the query.
	 * @throws TopcatException
	 */
	private JsonArray submitQuery(String query) throws TopcatException {
		try {
			String encodedQuery = URLEncoder.encode(query, "UTF8");
			String url = "entityManager?sessionId=" + URLEncoder.encode(sessionId, "UTF8") + "&query=" + encodedQuery;
			Response response = httpClient.get(url, new HashMap<String, String>());
			if (response.getCode() == 404) {
				throw new NotFoundException("Could not run getEntities got a 404 response");
			} else if (response.getCode() >= 400) {
				throw new BadRequestException(Utils.parseJsonObject(response.toString()).getString("message"));
			}
			return Utils.parseJsonArray(response.toString());
		} catch (TopcatException e) {
            throw e;		} catch (Exception e) {
				throw new BadRequestException(e.getMessage());
			}
		}
	
	/**
	 * Gets a single Entity of the specified type, without any other conditions.
	 * 
	 * @param entityType Type of ICAT Entity to get
	 * @return A single ICAT Entity of the specified type as a JsonObject
	 * @throws TopcatException
	 */
	public JsonObject getEntity(String entityType) throws TopcatException {
		try {
			String entityCapital = StringUtils.capitalize(entityType.toLowerCase());
			String query = URLEncoder.encode("SELECT o FROM " + entityCapital + " o LIMIT 0, 1", "UTF8");
			String url = "entityManager?sessionId="  + URLEncoder.encode(sessionId, "UTF8") + "&query=" + query;
			Response response = httpClient.get(url, new HashMap<String, String>());
			if(response.getCode() == 404){
				throw new NotFoundException("Could not run getEntity got a 404 response");
			} else if(response.getCode() >= 400){
				throw new BadRequestException(Utils.parseJsonObject(response.toString()).getString("message"));
			}
			JsonObject entity = Utils.parseJsonArray(response.toString()).getJsonObject(0);
			return entity.getJsonObject(entityCapital);
		} catch (TopcatException e){
			throw e;
		} catch (Exception e) {
			throw new BadRequestException(e.getMessage());
		}
	}

	public List<JsonObject> getEntities(String entityType, List<Long> entityIds) throws TopcatException {
		List<JsonObject> out = new ArrayList<JsonObject>();
		try {
			entityIds = new ArrayList<Long>(entityIds);

			String queryPrefix;
			String querySuffix;

			if (entityType.equals("datafile")) {
				queryPrefix = "SELECT datafile from Datafile datafile where datafile.id in (";
				querySuffix = ") include datafile.dataset.investigation";
			} else if (entityType.equals("dataset")) {
				queryPrefix = "SELECT dataset from Dataset dataset where dataset.id in (";
				querySuffix = ") include dataset.investigation";
			} else {
				queryPrefix = "SELECT investigation from Investigation investigation where investigation.id in (";
				querySuffix = ")";
			}

			StringBuffer currentCandidateEntityIds = new StringBuffer();
			String currentPassedUrl = null;
			String currentCandidateUrl = null;

			List<String> passedUrls = new ArrayList<String>();

			while(entityIds.size() > 0){
				if (currentCandidateEntityIds.length() != 0) {
					currentCandidateEntityIds.append(",");
				}
				currentCandidateEntityIds.append(entityIds.get(0));
				currentCandidateUrl = "entityManager?sessionId="  + URLEncoder.encode(sessionId, "UTF8") + "&query=" + URLEncoder.encode(queryPrefix + currentCandidateEntityIds.toString() + querySuffix , "UTF8");
				if(httpClient.urlLength(currentCandidateUrl) > 2048){
					currentCandidateEntityIds = new StringBuffer();
					if(currentPassedUrl == null){
						break;
					}
					passedUrls.add(currentPassedUrl);
					currentPassedUrl = null;
				} else {
					currentPassedUrl = currentCandidateUrl;
					currentCandidateUrl = null;
					entityIds.remove(0);
				}
			}

			if(currentPassedUrl != null){
				passedUrls.add(currentPassedUrl);
			}

			for(String passedUrl : passedUrls){
				Response response = httpClient.get(passedUrl, new HashMap<String, String>());

				if(response.getCode() == 404){
	                throw new NotFoundException("Could not run getEntities got a 404 response");
	            } else if(response.getCode() >= 400){
	                throw new BadRequestException(Utils.parseJsonObject(response.toString()).getString("message"));
	            }

				for(JsonValue entityValue : Utils.parseJsonArray(response.toString())){
					JsonObject entity = (JsonObject) entityValue;
					out.add(entity.getJsonObject(entityType.substring(0, 1).toUpperCase() + entityType.substring(1)));
				}
			}
		} catch (TopcatException e){
            throw e;
		} catch (Exception e) {
			throw new BadRequestException(e.getMessage());
		}

		return out;
	}

	/**
	 * @param userName ICAT User.name to check for access to the queue
	 * @throws TopcatException If the user has a non-positive priority value (or
	 *                         another internal error is triggered)
	 */
	public void checkQueueAllowed(String userName) throws TopcatException {
		if (getQueuePriority(userName) < 1) {
			throw new ForbiddenException("Queuing Downloads forbidden");
		}
	}

	/**
	 * If explicitly set via InstrumentScientist or InvestigationUser mappings,
	 * the highest priority (lowest value) will be returned.
	 * Otherwise, if authenticated, the authenticated user default will be returned.
	 * Otherwise, global default will be returned.
	 * 
	 * @param userName ICAT User.name to determine the queue priority of
	 * @return int representing the queue priority. <1 indicates disabled, >=1
	 *         indicates enabled with higher values having lower priority.
	 * @throws TopcatException
	 */
	public int getQueuePriority(String userName) throws TopcatException {
		PriorityMap priorityMap = PriorityMap.getInstance();
		HashMap<Integer, String> mapping = priorityMap.getMapping();
		List<Integer> keyList = new ArrayList<>(mapping.keySet());
		Collections.sort(keyList);
		for (Integer priority : keyList) {
			if (checkUser(userName, mapping.get(priority)) > 0) {
				return priority;
			}
		}

		if (!userName.equals(Properties.getInstance().getProperty("anonUserName"))) {
			return priorityMap.getAuthenticatedPriority();
		} else {
			return priorityMap.getDefaultPriority();
		}
	}

	/**
	 * @param userName  ICAT User.name to determine the queue priority of
	 * @param condition JPQL condition representing the possible ways a user can
	 *                  have priority
	 * @return size of the results, 0 means use did not have priority, 1 means they
	 *         did
	 * @throws TopcatException
	 */
	int checkUser(String userName, String condition) throws TopcatException {
		String query = "SELECT user FROM User user WHERE user.name = '" + userName + "' AND (" + condition + ")";
		JsonArray results = submitQuery(query);
		return results.size();
	}

	protected String[] getAdminUserNames() throws Exception {
		return Properties.getInstance().getProperty("adminUserNames", "").split("([ ]*,[ ]*|[ ]+)");
	}

}

