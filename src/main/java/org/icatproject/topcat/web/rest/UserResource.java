package org.icatproject.topcat.web.rest;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import jakarta.ejb.EJB;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;

import jakarta.json.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.GenericEntity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.DefaultValue;

import org.icatproject.topcat.domain.*;
import org.icatproject.topcat.exceptions.*;
import org.icatproject.topcat.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.icatproject.topcat.IdsClient;
import org.icatproject.topcat.PriorityMap;
import org.icatproject.topcat.FacilityMap;
import org.icatproject.topcat.IcatClient;
import org.icatproject.topcat.Properties;
import org.icatproject.topcat.IcatClient.DatafilesResponse;

@Stateless
@LocalBean
@Path("user")
public class UserResource {

	private static final Logger logger = LoggerFactory.getLogger(UserResource.class);
	

	@EJB
	private DownloadRepository downloadRepository;

	@EJB
	private DownloadTypeRepository downloadTypeRepository;

	@EJB
	private CartRepository cartRepository;

	@EJB
	private CacheRepository cacheRepository;

	private String anonUserName;
	private String defaultPlugin;
	private long queueVisitMaxPartFileCount;
	private long queueFilesMaxFileCount;

	@PersistenceContext(unitName = "topcat")
	EntityManager em;

	public UserResource() {
		Properties properties = Properties.getInstance();
		this.anonUserName = properties.getProperty("anonUserName", "");
		this.defaultPlugin = properties.getProperty("defaultPlugin", "simple");
		this.queueVisitMaxPartFileCount = Long.valueOf(properties.getProperty("queue.visit.maxPartFileCount", "10000"));
		this.queueFilesMaxFileCount = Long.valueOf(properties.getProperty("queue.files.maxFileCount", "10000"));
    }

	/**
	 * Returns the cart userName, which is either the ICAT userName if the user isn't anonUserName,
	 * or it's the ICAT userName plus the sessionId if it is the anon user name
	 */
	private String getCartUserName(String userName, String sessionId) {
		if (userName.equals(this.anonUserName)) {
			return userName + "/" + sessionId;
		} else {
			return userName;
		}
	}

	/**
	 * Login to create a session
	 * 
	 * @param facilityName A facility name - properties must map this to a url to a valid ICAT REST api, if set.
	 * @param username     ICAT username
	 * @param password     Password for the specified authentication plugin
	 * @param plugin       ICAT authentication plugin. If null, a default value will be used.
	 * @return json with sessionId of the form
	 *         <samp>{"sessionId","0d9a3706-80d4-4d29-9ff3-4d65d4308a24"}</samp>
	 * @throws BadRequestException
	 */
	@POST
	@Path("/session")
	public String login(@QueryParam("facilityName") String facilityName, @FormParam("username") String username, @FormParam("password") String password, @FormParam("plugin") String plugin) throws BadRequestException {
		if (plugin == null) {
			plugin = defaultPlugin;
		}
		String icatUrl = getIcatUrl(facilityName);
		IcatClient icatClient = new IcatClient(icatUrl);
		return icatClient.login(plugin, username, password);
	}

	/**
	 * Returns a list of downloads associated with a particular sessionId
	 * filtered by a partial JPQL expression.
	 *
	 * @summary getDownloads
	 *
	 * @param facilityName
	 *            a facility name - properties must map this to a url to a valid ICAT REST api.
	 * 
	 * @param sessionId
	 *            a valid session id which takes the form
	 *            <code>0d9a3706-80d4-4d29-9ff3-4d65d4308a24</code>
	 *
	 * @param queryOffset
	 *            any JPQL expression (*) that can be appended to
	 *            "SELECT download from Download download", e.g.
	 *            "where download.isDeleted = false". Note that like ICAT the
	 *            syntax has been extended allowing (sql like) limit clauses in
	 *            the form "limit [offset], [row count]" e.g. "limit 10, 20".
	 *            Note the "status" attribute is an enum (not a string) i.e.
	 *            org.icatproject.topcat.domain.Status with the following
	 *            possible states: 'ONLINE', 'ARCHIVE' or 'RESTORING'. So an
	 *            example query involving the status attribute could be
	 *            "where download.status = org.icatproject.topcat.domain.Status.ARCHIVE limit 0, 10"
	 *            (*) Note: the expression must not contain closing brackets (")").
	 *
	 * @return returns an array of downloads in the form
	 *         [{"completedAt":"2016-03-18T16:02:36","createdAt":
	 *         "2016-03-18T16:02:36","deletedAt":"2016-03-18T16:02:47",
	 *         "downloadItems":[{"entityId":18064,"entityType":"datafile","id":2
	 *         },{"entityId":18061,"entityType":"datafile","id":3}],"email":"",
	 *         "facilityName":"test","fileName":"test_2016-3-18_16-05-59",
	 *         "id":2,"isDeleted":false,
	 *         "isTwoLevel":false,"preparedId":
	 *         "6d3aaca5-da9f-4e6a-922d-eceeefcc07e0","status":"COMPLETE",
	 *         "size":324675,"transport":"https",
	 *         "userName":"simple/root"}]
	 *
	 * @throws MalformedURLException
	 *             if facilityName is invalid.
	 *
	 * @throws ParseException
	 *             if a JPQL query is malformed.
	 * 
	 * @throws TopcatException
	 *             if anything else goes wrong.
	 */
	@GET
	@Path("/downloads")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response getDownloads(@QueryParam("facilityName") String facilityName, @QueryParam("sessionId") String sessionId,
			@QueryParam("queryOffset") String queryOffset)
			throws TopcatException, MalformedURLException, ParseException {

		String icatUrl = getIcatUrl( facilityName );
		IcatClient icatClient = new IcatClient(icatUrl, sessionId);

		Map<String, Object> params = new HashMap<String, Object>();
		// Note: we believe that userName can never be null/empty
		String cartUserName = getCartUserName(icatClient.getUserName(), sessionId);
		params.put("userName", cartUserName);
		params.put("queryOffset", queryOffset);

		List<Download> downloads = new ArrayList<Download>();
		downloads = downloadRepository.getDownloads(params);

		return Response.ok().entity(new GenericEntity<List<Download>>(downloads) {
		}).build();
	}

	/**
	 * Get the statuses of one or more in progress Downloads.
	 * 
	 * @param facilityName ICAT Facility.name
	 * @param sessionId    ICAT sessionId, only carts belonging to the ICAT user
	 *                     this resolves to will be returned.
	 * @param downloadIds  One or more ids for the Download(s) to check
	 * @return Array of DownloadStatus values
	 * @throws TopcatException
	 * @throws MalformedURLException
	 * @throws ParseException
	 */
	@GET
	@Path("/downloads/status")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response getDownloadStatuses(@QueryParam("facilityName") String facilityName,
			@QueryParam("sessionId") String sessionId, @QueryParam("downloadIds") List<Long> downloadIds)
			throws TopcatException, MalformedURLException, ParseException {

		if (downloadIds.size() == 0) {
			throw new BadRequestException("At least one downloadId required");
		}
		String icatUrl = getIcatUrl(facilityName);
		IcatClient icatClient = new IcatClient(icatUrl, sessionId);
		String cartUserName = getCartUserName(icatClient.getUserName(), sessionId);
		List<DownloadStatus> statuses = downloadRepository.getStatuses(cartUserName, downloadIds);

		return Response.ok().entity(statuses).build();
	}

	/**
	 * Sets whether or not a download is deleted associated with a particular
	 * sessionId.
	 *
	 * @summary deleteDownload
	 *
	 * @param facilityName
	 *            a facility name - properties must map this to a url to a valid ICAT REST api.
	 * 
	 * @param sessionId
	 *            a valid session id which takes the form
	 *            <code>0d9a3706-80d4-4d29-9ff3-4d65d4308a24</code>
	 *
	 * @param id
	 *            the download id in the database.
	 *
	 * @param value
	 *            either true or false.
	 *
	 * @return an empty Response
	 * 
	 * @throws MalformedURLException
	 *             if facilityName is invalid.
	 *
	 * @throws ParseException
	 *             if a JPQL query is malformed.
	 * 
	 * @throws TopcatException
	 *             if anything else goes wrong.
	 */
	@PUT
	@Path("/download/{id}/isDeleted")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response deleteDownload(@PathParam("id") Long id, @FormParam("facilityName") String facilityName,
			@FormParam("sessionId") String sessionId, @FormParam("value") Boolean value)
			throws TopcatException, MalformedURLException, ParseException {

		Download download = downloadRepository.getDownload(id);
		if (download == null) {
			throw new NotFoundException("could not find download");
		}

		String icatUrl = getIcatUrl( facilityName );
		IcatClient icatClient = new IcatClient(icatUrl, sessionId);

		String userName = icatClient.getUserName();
		String cartUserName = getCartUserName(userName, sessionId);
		if (!download.getUserName().equals(cartUserName)) {
			throw new ForbiddenException("you do not have permission to delete this download");
		}

		download.setIsDeleted(value);
		if (value) {
			download.setDeletedAt(new Date());
		}

		downloadRepository.save(download);

		return Response.ok().build();
	}

	/**
     * Sets the download status.
     *
     * @summary setDownloadStatus
     *
	 * @param facilityName
	 *            a facility name - properties must map this to a url to a valid ICAT REST api.
     * 
     * @param sessionId a valid session id which takes the form <code>0d9a3706-80d4-4d29-9ff3-4d65d4308a24</code> 
     *
     * @param id the download id in the database.
     *
     * @param value the status value i.e. 'ONLINE', 'ARCHIVE' or 'RESTORING'.
     *
     * @return an empty Response
     * 
     * @throws MalformedURLException if facilityName is invalid.
     *
     * @throws ParseException if a JPQL query is malformed.
     * 
     * @throws TopcatException if anything else goes wrong.
     */
    @PUT
    @Path("/download/{id}/status")
    @Produces({MediaType.APPLICATION_JSON})
    public Response setDownloadStatus(
        @PathParam("id") Long id,
        @FormParam("facilityName") String facilityName,
        @FormParam("sessionId") String sessionId,
        @FormParam("value") String value)
        throws TopcatException, MalformedURLException, ParseException {

        Download download = downloadRepository.getDownload(id);

        if(download == null){
            throw new NotFoundException("could not find download");
        }

        String icatUrl = getIcatUrl( facilityName );
        IcatClient icatClient = new IcatClient(icatUrl, sessionId);

		String userName = icatClient.getUserName();
		String cartUserName = getCartUserName(userName, sessionId);
		if (!download.getUserName().equals(cartUserName)) {
			throw new ForbiddenException("you do not have permission to delete this download");
		}
        if (download.getPreparedId() == null) {
            throw new ForbiddenException("Cannot modify status of a download before it's prepared");
        }

        download.setStatus(DownloadStatus.valueOf(value));
        if(value.equals("COMPLETE")){
            download.setCompletedAt(new Date());
        }

        downloadRepository.save(download);

        return Response.ok().build();
	}

	/**
	 * Returns the cart object associated with a particular sessionId and
	 * facility.
	 *
	 * @summary getCart
	 *
	 * @param sessionId
	 *            a valid session id which takes the form
	 *            <code>0d9a3706-80d4-4d29-9ff3-4d65d4308a24</code>
	 *
	 * @param facilityName
	 *            the name of the facility e.g. 'dls'.
	 *            properties must map this to a url to a valid ICAT REST api.
	 *
	 * @return returns the cart object in the form:
	 *         {"cartItems":[{"entityId":18178,"entityType":"datafile","id":1,
	 *         "name":"tenenvironment.rhy","parentEntities":[{"entityId":182,
	 *         "entityType":"investigation","id":1},{"entityId":1818,
	 *         "entityType":"dataset","id":2}]},{"entityId":181,"entityType":
	 *         "investigation","id":2,"name":"APPLIEDAHEAD","parentEntities":[]}
	 *         ],"createdAt":"2016-03-30T10:52:32","facilityName":"example","id"
	 *         :1,"updatedAt":"2016-03-30T10:52:32","userName":"simple/root"}
	 *
	 * @throws MalformedURLException
	 *             if facilityName is invalid.
	 *
	 * @throws ParseException
	 *             if a JPQL query is malformed.
	 * 
	 * @throws TopcatException
	 *             if anything else goes wrong.
	 */
	@GET
	@Path("/cart/{facilityName}")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response getCart(@PathParam("facilityName") String facilityName, 
			@QueryParam("sessionId") String sessionId) throws TopcatException, MalformedURLException, ParseException {

        String icatUrl = getIcatUrl( facilityName );
		IcatClient icatClient = new IcatClient(icatUrl, sessionId);

		String userName = icatClient.getUserName();
		String cartUserName = getCartUserName(userName, sessionId);
		Cart cart = cartRepository.getCart(cartUserName, facilityName);

		if (cart != null) {
			em.refresh(cart);
			return Response.ok().entity(cart).build();
		} else {
			return emptyCart(facilityName, cartUserName);
		}
	}

	/**
	 * Adds items to the cart associated with a particular sessionId and
	 * facility.
	 *
	 * @summary addCartItems
	 *
	 * @param sessionId
	 *            a valid session id which takes the form
	 *            <code>0d9a3706-80d4-4d29-9ff3-4d65d4308a24</code>
	 *
	 * @param facilityName
	 *            the name of the facility e.g. 'dls'.
	 *            Properties must map this to a url to a valid ICAT REST api.
	 *
	 * @param items
	 *            a list of entity type (i.e. datafile, dataset or
	 *            investigation) and entity id pairs in the form: investigation
	 *            2, datafile 1
	 *
	 * @param remove
	 *            flag to determine whether the request should be used to remove
	 *            items from the cart or not. If set to true, the items given in
	 *            the request will be removed from the cart (equivalent to the
	 *            DELETE endpoint). The default is to add to the cart (i.e.
	 *            false)
	 *
	 * @return returns the cart object in the form:
	 *         {"cartItems":[{"entityId":18178,"entityType":"datafile","id":1,
	 *         "name":"tenenvironment.rhy","parentEntities":[{"entityId":182,
	 *         "entityType":"investigation","id":1},{"entityId":1818,
	 *         "entityType":"dataset","id":2}]},{"entityId":181,"entityType":
	 *         "investigation","id":2,"name":"APPLIEDAHEAD","parentEntities":[]}
	 *         ],"createdAt":"2016-03-30T10:52:32","facilityName":"example","id"
	 *         :1,"updatedAt":"2016-03-30T10:52:32","userName":"simple/root"}
	 *
	 * @throws MalformedURLException
	 *             if facilityName is invalid.
	 *
	 * @throws ParseException
	 *             if a JPQL query is malformed.
	 * 
	 * @throws TopcatException
	 *             if anything else goes wrong.
	 */
	@POST
	@Path("/cart/{facilityName}/cartItems")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response addCartItems(@PathParam("facilityName") String facilityName, 
			@FormParam("sessionId") String sessionId, @FormParam("items") String items, @DefaultValue("false") @FormParam("remove") Boolean remove)
			throws TopcatException, MalformedURLException, ParseException {

		logger.info("addCartItems() called");

		if (remove == true) {
			logger.info("Calling deleteCartItems() from addCartItems()");
			Response deleteCartResponse = this.deleteCartItems(facilityName, sessionId, items);
			return deleteCartResponse;
		}

		String icatUrl = getIcatUrl( facilityName );
		IcatClient icatClient = new IcatClient(icatUrl, sessionId);

		String userName = icatClient.getUserName();
		String cartUserName = getCartUserName(userName, sessionId);
		Cart cart = cartRepository.getCart(cartUserName, facilityName);

		if (cart == null) {
			cart = new Cart();
			cart.setFacilityName(facilityName);
			cart.setUserName(cartUserName);
			em.persist(cart);
			em.flush();
		}

		Map<Long, Boolean> isInvestigationIdIndex = new HashMap<Long, Boolean>();
        Map<Long, Boolean> isDatasetIdIndex = new HashMap<Long, Boolean>();
        Map<Long, Boolean> isDatafileIdIndex = new HashMap<Long, Boolean>();

        for(CartItem cartItem : cart.getCartItems()){
            if(cartItem.getEntityType().equals(EntityType.valueOf("investigation"))){
                isInvestigationIdIndex.put(cartItem.getEntityId(), true);
            } else if(cartItem.getEntityType().equals(EntityType.valueOf("dataset"))){
                isDatasetIdIndex.put(cartItem.getEntityId(), true);
            } else {
            	isDatafileIdIndex.put(cartItem.getEntityId(), true);
            }
        }

		List<Long> investigationIdsToAdd = new ArrayList<Long>();
		List<Long> datasetIdsToAdd = new ArrayList<Long>();
		List<Long> datafileIdsToAdd = new ArrayList<Long>();


		for (String item : items.split("\\s*,\\s*")) {
			String[] pair = item.split("\\s+");
			if (pair.length == 2) {
				String entityType = pair[0];
				Long entityId = Long.parseLong(pair[1]);
				if(entityType.equals("investigation") && isInvestigationIdIndex.get(entityId) != null){
					continue;
				} else if(entityType.equals("dataset") && isDatasetIdIndex.get(entityId) != null){
					continue;
				} else if(entityType.equals("datafile") && isDatafileIdIndex.get(entityId) != null){
					continue;
				}

				if(entityType.equals("investigation")){
					investigationIdsToAdd.add(entityId);
					isInvestigationIdIndex.put(entityId, true);
				} else if(entityType.equals("dataset")){
					datasetIdsToAdd.add(entityId);
					isDatasetIdIndex.put(entityId, true);
				} else {
					datafileIdsToAdd.add(entityId);
				}
			}
		}

		addEntitiesToCart(icatClient, cart, "investigation", investigationIdsToAdd);
		addEntitiesToCart(icatClient, cart, "dataset", datasetIdsToAdd);
		addEntitiesToCart(icatClient, cart, "datafile", datafileIdsToAdd);

		em.flush();
		em.refresh(cart);

		//remove any entities that have a parent added to the cart

        for(CartItem cartItem : cart.getCartItems()){
            for(ParentEntity parentEntity : cartItem.getParentEntities()){
                if(parentEntity.getEntityType().equals(EntityType.valueOf("investigation")) && isInvestigationIdIndex.get(parentEntity.getEntityId()) != null){
                    em.remove(cartItem);
                    break;
                } else if(parentEntity.getEntityType().equals(EntityType.valueOf("dataset")) && isDatasetIdIndex.get(parentEntity.getEntityId()) != null){
                    em.remove(cartItem);
                    break;
                }
            }
        }

		em.flush();
		em.refresh(cart);

		return Response.ok().entity(cart).build();
	}


	private void addEntitiesToCart(IcatClient icatClient, Cart cart, String entityType, List<Long> entityIds) throws TopcatException {
		if(entityIds.size() == 0){
			return;
		}	

		for (JsonObject entity : icatClient.getEntities(entityType, entityIds)) {
			String name = entity.getString("name");
			Long entityId = Long.valueOf(entity.getJsonNumber("id").longValue());

			CartItem cartItem = new CartItem();
			cartItem.setCart(cart);
			cartItem.setEntityType(EntityType.valueOf(entityType));
			cartItem.setEntityId(entityId);
			cartItem.setName(name);
			em.persist(cartItem);


			if (entityType.equals("datafile")) {
				ParentEntity parentEntity = new ParentEntity();
				parentEntity.setCartItem(cartItem);
				parentEntity.setEntityType(EntityType.valueOf("dataset"));
				parentEntity.setEntityId(Long.valueOf(entity.getJsonObject("dataset").getJsonNumber("id").longValue()));
				cartItem.getParentEntities().add(parentEntity);
				em.persist(parentEntity);

				parentEntity = new ParentEntity();
				parentEntity.setEntityType(EntityType.valueOf("investigation"));
				parentEntity.setEntityId(Long.valueOf(entity.getJsonObject("dataset").getJsonObject("investigation").getJsonNumber("id").longValue()));
				cartItem.getParentEntities().add(parentEntity);
				em.persist(parentEntity);

			} else if (entityType.equals("dataset")) {
				ParentEntity parentEntity = new ParentEntity();
				parentEntity.setEntityType(EntityType.valueOf("investigation"));
				parentEntity.setEntityId(Long.valueOf(entity.getJsonObject("investigation").getJsonNumber("id").longValue()));
				cartItem.getParentEntities().add(parentEntity);
				em.persist(parentEntity);
			}
		}
	}

	/**
	 * Deletes items from the cart associated with a particular sessionId and
	 * facility.
	 *
	 * @summary deleteCartItems
	 *
	 * @param sessionId
	 *            a valid session id which takes the form
	 *            <code>0d9a3706-80d4-4d29-9ff3-4d65d4308a24</code>
	 *
	 * @param facilityName
	 *            the name of the facility e.g. 'dls'.
	 *            Properties must map this to a url to a valid ICAT REST api.
	 *
	 * @param items
	 *            a list of entity type (i.e. datafile, dataset or
	 *            investigation) and entity id pairs, in the form: investigation
	 *            2, datafile 1. Or a list cart item ids in the form: 45, 56.
	 * 
	 * @return returns the cart object in the form:
	 *         {"cartItems":[{"entityId":18178,"entityType":"datafile","id":1,
	 *         "name":"tenenvironment.rhy","parentEntities":[{"entityId":182,
	 *         "entityType":"investigation","id":1},{"entityId":1818,
	 *         "entityType":"dataset","id":2}]},{"entityId":181,"entityType":
	 *         "investigation","id":2,"name":"APPLIEDAHEAD","parentEntities":[]}
	 *         ],"createdAt":"2016-03-30T10:52:32","facilityName":"example","id"
	 *         :1,"updatedAt":"2016-03-30T10:52:32","userName":"simple/root"}
	 *
	 * @throws MalformedURLException
	 *             if facilityName is invalid.
	 *
	 * @throws ParseException
	 *             if a JPQL query is malformed.
	 * 
	 * @throws TopcatException
	 *             if anything else goes wrong.
	 */
	@DELETE
	@Path("/cart/{facilityName}/cartItems")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response deleteCartItems(@PathParam("facilityName") String facilityName,
			@QueryParam("sessionId") String sessionId,
			@QueryParam("items") String items) throws TopcatException, MalformedURLException, ParseException {

		String icatUrl = getIcatUrl( facilityName );
		IcatClient icatClient = new IcatClient(icatUrl, sessionId);

		String userName = icatClient.getUserName();
		String cartUserName = getCartUserName(userName, sessionId);
		Cart cart = cartRepository.getCart(cartUserName, facilityName);
		if (cart == null) {
			return emptyCart(facilityName, cartUserName);
		}

		if (items.equals("*")) {
			for (CartItem cartItem : cart.getCartItems()) {
				em.remove(cartItem);
			}
		} else {
			for (String item : items.split("\\s*,\\s*")) {
				String[] pair = item.split("\\s+");

				if (pair.length > 1) {
					String entityType = pair[0];
					Long entityId = Long.parseLong(pair[1]);

					for (CartItem cartItem : cart.getCartItems()) {
						boolean entityTypesMatch = cartItem.getEntityType().equals(EntityType.valueOf(entityType));
						boolean entityIdsMatch = cartItem.getEntityId().equals(entityId);
						if (entityTypesMatch && entityIdsMatch) {
							em.remove(cartItem);
						}
					}
				} else {
					Long id = Long.parseLong(pair[0]);
					for (CartItem cartItem : cart.getCartItems()) {
						if (cartItem.getId().equals(id)) {
							em.remove(cartItem);
							break;
						}
					}
				}
			}
		}

		em.flush();
		em.refresh(cart);

		if (cart.getCartItems().size() == 0) {
			em.remove(cart);
			em.flush();
			return emptyCart(facilityName, cartUserName);
		}

		return Response.ok().entity(cart).build();
	}

	/**
	 * Submits a cart which creates a download.
	 *
	 * @summary submitCart
	 *
	 * @param sessionId
	 *            a valid session id which takes the form
	 *            <code>0d9a3706-80d4-4d29-9ff3-4d65d4308a24</code>
	 *
	 * @param facilityName
	 *            the name of the facility e.g. 'dls'.
	 *            Properties must map this to a url to a valid ICAT REST api.
	 *
	 * @param transport
	 *            the type of delivery method e.g. 'https' or 'globus' etc...
	 *
	 * @param email
	 *            an optional email to send download status messages to e.g. if
	 *            the download is prepared
	 *
	 * @param fileName
	 *            the name of the zip file containing the downloads.
	 *
	 * @param zipType
	 *            zip compressing options can be 'ZIP' (default) or
	 *            'ZIP_AND_COMPRESS'
	 *
	 * @return returns the (empty) cart object (with downloadId) in the form:
	 *         {"facilityName":"test","userName":"simple/root","cartItems":[],
	 *         "downloadId":3}
	 *
	 * @throws MalformedURLException
	 *             if facilityName is invalid.
	 *
	 * @throws ParseException
	 *             if a JPQL query is malformed.
	 * 
	 * @throws TopcatException
	 *             if anything else goes wrong.
	 */
	@POST
	@Path("/cart/{facilityName}/submit")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response submitCart(@PathParam("facilityName") String facilityName,
			@FormParam("sessionId") String sessionId,
			@FormParam("transport") String transport,
			@FormParam("email") String email,
			@FormParam("fileName") String fileName,
			@FormParam("zipType") String zipType)
			throws TopcatException, MalformedURLException, ParseException {

		logger.info("submitCart called");

		if (fileName == null || fileName.trim().isEmpty()) {
			throw new BadRequestException("fileName is required");
		}

		validateTransport(transport);

		String icatUrl = getIcatUrl( facilityName );
		IcatClient icatClient = new IcatClient(icatUrl, sessionId);
		String userName = icatClient.getUserName();
		PriorityMap priorityMap = PriorityMap.getInstance();
		priorityMap.checkAnonDownloadEnabled(userName);
		String cartUserName = getCartUserName(userName, sessionId);

		logger.info("submitCart: get cart for user: " + cartUserName + ", facility: " + facilityName + "...");

		Cart cart = cartRepository.getCart(cartUserName, facilityName);
		String fullName = icatClient.getFullName();
		Long downloadId = null;
		String transportUrl = getDownloadUrl(facilityName, transport);
		IdsClient idsClient = new IdsClient(transportUrl);

		if(email != null && email.equals("")){
			email = null;
		}


		if (cart != null) {
			em.refresh(cart);
			Download download = createDownload(sessionId, cart.getFacilityName(), fileName, cart.getUserName(),
					fullName, transport, email);
			List<DownloadItem> downloadItems = new ArrayList<DownloadItem>();
			for (CartItem cartItem : cart.getCartItems()) {
				DownloadItem downloadItem = createDownloadItem(download, cartItem.getEntityId(),
						cartItem.getEntityType());
				downloadItems.add(downloadItem);
			}
			download.setDownloadItems(downloadItems);
			downloadId = submitDownload(idsClient, download, DownloadStatus.PREPARING);
			try {
				em.remove(cart);
				em.flush();
			} catch (Exception e) {
				logger.info("submitCart: exception during EntityManager operations: " + e.getMessage());
				throw new BadRequestException("Unable to submit for cart for download");
			}
		}

		return emptyCart(facilityName, cartUserName, downloadId);
	}

	/**
	 * Create a new Download object and set basic fields, excluding data and status.
	 * 
	 * @param sessionId    ICAT sessionId
	 * @param facilityName ICAT Facility.name
	 * @param fileName     Filename for the resultant Download
	 * @param userName     ICAT User.name
	 * @param fullName     ICAT User.fullName
	 * @param transport    Transport mechanism to use
	 * @param email        Optional email to send notification to on completion
	 * @return Download object with basic fields set
	 */
	private static Download createDownload(String sessionId, String facilityName, String fileName, String userName,
			String fullName, String transport, String email) {
		Download download = new Download();
		download.setSessionId(sessionId);
		download.setFacilityName(facilityName);
		download.setFileName(fileName);
		download.setUserName(userName);
		download.setFullName(fullName);
		download.setTransport(transport);
		download.setEmail(email);
		download.setIsEmailSent(false);
		download.setSize(0);
		return download;
	}

	/**
	 * Create a new DownloadItem.
	 * 
	 * @param download   Parent Download
	 * @param entityId   ICAT Entity.id
	 * @param entityType EntityType
	 * @return DownloadItem with fields set
	 */
	private static DownloadItem createDownloadItem(Download download, long entityId, EntityType entityType) {
		DownloadItem downloadItem = new DownloadItem();
		downloadItem.setEntityId(entityId);
		downloadItem.setEntityType(entityType);
		downloadItem.setDownload(download);
		return downloadItem;
	}

	/**
	 * Set the final fields and persist a new Download request.
	 * 
	 * @param idsClient      Client for the IDS to use for the Download
	 * @param download       Download to submit
	 * @param downloadStatus Initial DownloadStatus to set if and only if the IDS isTwoLevel
	 * @return Id of the new Download
	 * @throws TopcatException
	 */
	private long submitDownload(IdsClient idsClient, Download download, DownloadStatus downloadStatus)
			throws TopcatException {
		Boolean isTwoLevel = idsClient.isTwoLevel();
		download.setIsTwoLevel(isTwoLevel);

		if (isTwoLevel) {
			download.setStatus(downloadStatus);
		} else {
			String preparedId = idsClient.prepareData(download.getSessionId(), download.getInvestigationIds(),
					download.getDatasetIds(), download.getDatafileIds());
			download.setPreparedId(preparedId);
			download.setStatus(DownloadStatus.COMPLETE);
		}

		try {
			em.persist(download);
			em.flush();
			em.refresh(download);
			return download.getId();
		} catch (Exception e) {
			logger.info("submitCart: exception during EntityManager operations: " + e.getMessage());
			throw new BadRequestException("Unable to submit for cart for download");
		}
	}

	/**
	 * Queue an entire visit for download, split by Dataset into part Downloads if
	 * needed.
	 * 
	 * @param facilityName ICAT Facility.name
	 * @param sessionId    ICAT sessionId
	 * @param transport    Transport mechanism to use
	 * @param fileName     Optional name to use as the root for each individual part
	 *                     Download. Defaults to facilityName_visitId.
	 * @param email        Optional email to notify upon completion
	 * @param visitId      ICAT Investigation.visitId to submit
	 * @return Array of Download ids
	 * @throws TopcatException
	 */
	@POST
	@Path("/queue/visit")
	public Response queueVisitId(@FormParam("facilityName") String facilityName,
			@FormParam("sessionId") String sessionId, @FormParam("transport") String transport,
			@FormParam("fileName") String fileName, @FormParam("email") String email,
			@FormParam("visitId") String visitId) throws TopcatException {

		if (visitId == null || visitId.equals("")) {
			throw new BadRequestException("visitId must be provided");
		}
		logger.info("queueVisitId called for {}", visitId);
		validateTransport(transport);

		String icatUrl = getIcatUrl(facilityName);
		IcatClient icatClient = new IcatClient(icatUrl, sessionId);
		String transportUrl = getDownloadUrl(facilityName, transport);
		IdsClient idsClient = new IdsClient(transportUrl);

		// If we wanted to block the user, this is where we would do it
		String userName = icatClient.getUserName();
		String fullName = icatClient.getFullName();
		icatClient.checkQueueAllowed(userName);
		JsonArray datasets = icatClient.getDatasets(visitId);
		if (datasets.size() == 0) {
			throw new NotFoundException("No Datasets found for " + visitId);
		}

		long downloadId;
		JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();

		long downloadFileCount = 0L;
		long downloadFileSize = 0L;
		List<DownloadItem> downloadItems = new ArrayList<DownloadItem>();
		List<Download> downloads = new ArrayList<Download>();
		Download newDownload = createDownload(sessionId, facilityName, "", userName, fullName, transport, email);

		for (JsonValue dataset : datasets) {
			JsonArray datasetArray = dataset.asJsonArray();
			long datasetId = datasetArray.getJsonNumber(0).longValueExact();
			long datasetFileCount = datasetArray.getJsonNumber(1).longValueExact();
			long datasetFileSize = datasetArray.getJsonNumber(2).longValueExact();
			// Database triggers should set these, but check explicitly anyway
			if (datasetFileCount < 1L) {
				datasetFileCount = icatClient.getDatasetFileCount(datasetId);
			}
			if (datasetFileSize < 1L) {
				datasetFileSize = icatClient.getDatasetFileSize(datasetId);
			}

			if (downloadFileCount > 0L && downloadFileCount + datasetFileCount > queueVisitMaxPartFileCount) {
				newDownload.setDownloadItems(downloadItems);
				newDownload.setSize(downloadFileSize);
				downloads.add(newDownload);

				downloadFileCount = 0L;
				downloadFileSize = 0L;
				downloadItems = new ArrayList<DownloadItem>();
				newDownload = createDownload(sessionId, facilityName, "", userName, fullName, transport, email);
			}

			DownloadItem downloadItem = createDownloadItem(newDownload, datasetId, EntityType.dataset);
			downloadItems.add(downloadItem);
			downloadFileCount += datasetFileCount;
			downloadFileSize += datasetFileSize;
		}
		newDownload.setDownloadItems(downloadItems);
		newDownload.setSize(downloadFileSize);
		downloads.add(newDownload);

		int part = 1;
		if (fileName == null) {
			fileName = facilityName + "_" + visitId;
		}
		for (Download download : downloads) {
			String partFilename = formatQueuedFilename(fileName, part, downloads.size());
			download.setFileName(partFilename);
			downloadId = submitDownload(idsClient, download, DownloadStatus.PAUSED);
			jsonArrayBuilder.add(downloadId);
			part += 1;
		}

		return Response.ok(jsonArrayBuilder.build()).build();
	}

	/**
	 * Check whether the current user is allowed to send large jobs to the queue.
	 * 
	 * @param facilityName ICAT Facility.name
	 * @param sessionId    ICAT sessionId, which will identify the user
	 * @return boolean
	 * @throws TopcatException
	 */
	@GET
	@Path("/queue/allowed")
	public Response queueAllowed(@QueryParam("sessionId") String sessionId,
			@QueryParam("facilityName") String facilityName) throws TopcatException {

		logger.info("queueAllowed called");

		String icatUrl = getIcatUrl(facilityName);
		IcatClient icatClient = new IcatClient(icatUrl, sessionId);
		String userName = icatClient.getUserName();
		int queuePriority = icatClient.getQueuePriority(userName);

		return Response.ok(queuePriority > 0).build();
	}

	/**
	 * Queue download of Datafiles by location, up to a configurable limit.
	 * 
	 * @param facilityName ICAT Facility.name
	 * @param sessionId    ICAT sessionId
	 * @param transport    Transport mechanism to use
	 * @param fileName     Optional name to use as the root for each individual part
	 *                     Download. Defaults to facilityName_visitId.
	 * @param email        Optional email to notify upon completion
	 * @param files        ICAT Datafile.locations to download
	 * @return The resultant downloadId and an array of any locations which could not
	 * 		   be found
	 * @throws TopcatException
	 * @throws UnsupportedEncodingException 
	 */
	@POST
	@Path("/queue/files")
	public Response queueFiles(@FormParam("facilityName") String facilityName,
			@FormParam("sessionId") String sessionId, @FormParam("transport") String transport,
			@FormParam("fileName") String fileName, @FormParam("email") String email,
			@FormParam("files") List<String> files) throws TopcatException, UnsupportedEncodingException {

		if (files == null || files.size() == 0) {
			throw new BadRequestException("At least one Datafile.location required");
		} else if (files.size() > queueFilesMaxFileCount) {
			throw new BadRequestException("Limit of " + queueFilesMaxFileCount + " files exceeded");
		}
		logger.info("queueFiles called for {} files", files.size());
		validateTransport(transport);
		if (fileName == null) {
			fileName = facilityName + "_files";
		}

		String icatUrl = getIcatUrl(facilityName);
		IcatClient icatClient = new IcatClient(icatUrl, sessionId);
		String transportUrl = getDownloadUrl(facilityName, transport);
		IdsClient idsClient = new IdsClient(transportUrl);

		String userName = icatClient.getUserName();
		String fullName = icatClient.getFullName();
		icatClient.checkQueueAllowed(userName);

		DatafilesResponse response = icatClient.getDatafiles(files);
		if (response.ids.size() == 0) {
			throw new NotFoundException("No Datafiles found");
		}

		List<DownloadItem> downloadItems = new ArrayList<>();
		Download download = createDownload(sessionId, facilityName, fileName, userName, fullName, transport, email);
		for (long datafileId : response.ids) {
			DownloadItem downloadItem = createDownloadItem(download, datafileId, EntityType.datafile);
			downloadItems.add(downloadItem);
		}
		download.setDownloadItems(downloadItems);
		download.setSize(response.totalSize);

		long downloadId = submitDownload(idsClient, download, DownloadStatus.PAUSED);

		JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
		JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
		for (String missingFile : response.missing) {
			jsonArrayBuilder.add(missingFile);
		}
		jsonObjectBuilder.add("downloadId", downloadId);
		jsonObjectBuilder.add("notFound", jsonArrayBuilder);
		return Response.ok(jsonObjectBuilder.build()).build();
	}

	/**
	 * Format the filename for a queued Download, possibly one part of many.
	 * 
	 * @param filename Root of the formatted filename, either user specified or defaulted.
	 * @param part     1 indexed part of the overall request
	 * @param size     Number of parts in the overall request
	 * @return Formatted filename
	 */
	private static String formatQueuedFilename(String filename, int part, int size) {
		String partString = String.valueOf(part);
		String sizeString = String.valueOf(size);
		StringBuilder partBuilder = new StringBuilder();
		while (partBuilder.length() + partString.length() < sizeString.length()) {
			partBuilder.append("0");
		}
		partBuilder.append(partString);

		StringBuilder filenameBuilder = new StringBuilder();
		filenameBuilder.append(filename);
		filenameBuilder.append("_part_");
		filenameBuilder.append(partBuilder);
		filenameBuilder.append("_of_");
		filenameBuilder.append(sizeString);
		return filenameBuilder.toString();
	}

	/**
	 * Validate that the submitted transport mechanism is not null or empty.
	 * 
	 * @param transport Transport mechanism to use
	 * @throws BadRequestException if null or empty
	 */
	private static void validateTransport(String transport) throws BadRequestException {
		if (transport == null || transport.trim().isEmpty()) {
			throw new BadRequestException("transport is required");
		}
	}

	/**
	 * Retrieves the total file size (in bytes) for any investigation, datasets or datafiles.
	 *
	 * @summary getSize
	 *
	 * @param facilityName
	 *            a facility name - properties must map this to a url to a valid ICAT REST api.
	 * 
	 * @param sessionId
	 *            a valid session id which takes the form
	 *            <code>0d9a3706-80d4-4d29-9ff3-4d65d4308a24</code>
	 *
	 * @param entityType
	 *            the type of entity 'investigation', 'dataset' or 'datafile'.
	 *
	 * @param entityId
	 *            a comma-separated-list of entity ids
	 *
	 * @return total size of the entities (as a Long)
	 * 
	 * @throws TopcatException
	 *             if anything else goes wrong.
	 */
	@GET
	@Path("/getSize")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response getSize(
		@QueryParam("facilityName") String facilityName,
		@QueryParam("sessionId") String sessionId,
		@QueryParam("entityType") String entityType,
		@QueryParam("entityId") Long entityId) throws TopcatException {

		String idsUrl = getIdsUrl( facilityName );
		IdsClient idsClient = new IdsClient(idsUrl);

		Long size = idsClient.getSize(cacheRepository, sessionId, entityType, entityId);

		return Response.ok().entity(size.toString()).build();
	}

	/**
	 * Query the enabled/disabled status of a download type. The default status is enabled.
	 * 
	 * @summary getDownloadTypeStatus
	 * 
	 * @param type
	 *            a download transport type name (as configured in topcat.json downloadTransportTypes[].type)
	 * @param facilityName
	 *            a facility name - properties must map this to a url to a valid ICAT REST api.
	 * 
	 * @param sessionId
	 *            a valid session id which takes the form
	 *            <code>0d9a3706-80d4-4d29-9ff3-4d65d4308a24</code>
	 *
	 * @return JSON object with disabled (boolean) and message (string) fields
	 * 
	 * @throws TopcatException
	 */
	@GET
	@Path("/downloadType/{type}/status")
	@Produces({MediaType.APPLICATION_JSON})
	public Response getDownloadTypeStatus(
			@PathParam("type") String type,
			@QueryParam("facilityName") String facilityName,
			@QueryParam("sessionId") String sessionId)
					throws TopcatException {
		  
		Boolean disabled = false;
		String message = "";
		DownloadType downloadType = downloadTypeRepository.getDownloadType(facilityName, type);
		  
		if( downloadType != null ) {
			disabled = downloadType.getDisabled();
			message = downloadType.getMessage();
		}

		JsonObjectBuilder responseJson = Json.createObjectBuilder()
				.add("disabled", disabled)
				.add("message", message);

		return Response.ok().entity(responseJson.build().toString()).build();
	}

	private Response emptyCart(String facilityName, String userName, Long downloadId) {
		JsonObjectBuilder emptyCart = Json.createObjectBuilder().add("facilityName", facilityName)
				.add("userName", userName).add("cartItems", Json.createArrayBuilder().build());

		if (downloadId != null) {
			emptyCart.add("downloadId", downloadId);
		}

		return Response.ok().entity(emptyCart.build().toString()).build();
	}

	private Response emptyCart(String facilityName, String userName) {
		return emptyCart(facilityName, userName, null);
	}
	
	private String getIcatUrl( String facilityName ) throws BadRequestException{
		try {
			return FacilityMap.getInstance().getIcatUrl(facilityName);
		} catch (InternalException ie){
			throw new BadRequestException( ie.getMessage() );
		}
	}

	private String getIdsUrl( String facilityName ) throws BadRequestException{
		try {
			return FacilityMap.getInstance().getIdsUrl(facilityName);
		} catch (InternalException ie){
			throw new BadRequestException( ie.getMessage() );
		}
	}

	private String getDownloadUrl( String facilityName, String downloadType ) throws BadRequestException{
		try {
			return FacilityMap.getInstance().getDownloadUrl(facilityName, downloadType);
		} catch (InternalException ie){
			throw new BadRequestException( ie.getMessage() );
		}
	}
}
