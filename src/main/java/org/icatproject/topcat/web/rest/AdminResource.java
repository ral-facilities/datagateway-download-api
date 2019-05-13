package org.icatproject.topcat.web.rest;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.text.ParseException;

import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.icatproject.topcat.domain.Download;
import org.icatproject.topcat.domain.DownloadStatus;
import org.icatproject.topcat.domain.DownloadType;
import org.icatproject.topcat.domain.ConfVar;
import org.icatproject.topcat.exceptions.TopcatException;
import org.icatproject.topcat.exceptions.NotFoundException;
import org.icatproject.topcat.exceptions.BadRequestException;
import org.icatproject.topcat.exceptions.ForbiddenException;
import org.icatproject.topcat.exceptions.InternalException;
import org.icatproject.topcat.repository.DownloadRepository;
import org.icatproject.topcat.repository.DownloadTypeRepository;
import org.icatproject.topcat.repository.CacheRepository;
import org.icatproject.topcat.repository.ConfVarRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.icatproject.topcat.FacilityMap;
import org.icatproject.topcat.IcatClient;

@Stateless
@LocalBean
@Path("admin")
public class AdminResource {
    private static final Logger logger = LoggerFactory.getLogger(AdminResource.class);

    @EJB
    private DownloadRepository downloadRepository;

    @EJB
    private DownloadTypeRepository downloadTypeRepository;

    @EJB
    private ConfVarRepository confVarRepository;

	@EJB
	private CacheRepository cacheRepository;

    /**
     * Returns whether or not the session provided has admin access - i.e. can use this "v1/admin/* api."
     *
     * @summary isValidSession
     *   
	 * @param facilityName
	 *            a facility name - properties must map this to a url to a valid ICAT REST api.
     * 
     * @param sessionId a valid session id which takes the form <code>0d9a3706-80d4-4d29-9ff3-4d65d4308a24</code> 
     *
     * @return "true" or "false" if the session has admin access or not.
     *
     * @throws MalformedURLException if facilityName is invalid.
     *
     * @throws TopcatException if anything else goes wrong.
     */
    @GET
    @Path("/isValidSession")
    @Produces({MediaType.APPLICATION_JSON})
    public Response isValidSession(
            @QueryParam("facilityName") String facilityName,
            @QueryParam("sessionId") String sessionId)
            throws MalformedURLException, TopcatException {
        logger.info("isValidSession() called");

        String icatUrl = getIcatUrl( facilityName );
        IcatClient icatClient = new IcatClient(icatUrl, sessionId);

        String isAdmin = icatClient.isAdmin() ? "true" : "false";

        return Response.ok().entity(isAdmin).build();
    }

    /**
     * Returns a list of downloads filtered by a partial JPQL expression.
     *
     * @summary getDownloads
     *
	 * @param facilityName
	 *            a facility name - properties must map this to a url to a valid ICAT REST api.
     * 
     * @param sessionId a valid session id which takes the form <code>0d9a3706-80d4-4d29-9ff3-4d65d4308a24</code> 
     *
     * @param queryOffset
     *  any JPQL expression that can be appended to "SELECT download from Download download",
     *  e.g. "where download.isDeleted = false". Note that like ICAT the syntax has been extended
     *  allowing (sql like) limit clauses in the form "limit [offset], [row count]" e.g. "limit 10, 20".
     *  Note the "status" attribute is an enum (not a string) i.e. org.icatproject.topcat.domain.Status
     *  with the following possible states: 'ONLINE', 'ARCHIVE' or 'RESTORING'. So an example query involving
     *  the status attribute could be "where download.status = org.icatproject.topcat.domain.Status.ARCHIVE limit 0, 10"
     *
     * @return returns an array of downloads in the form
     * [{"completedAt":"2016-03-18T16:02:36","createdAt":"2016-03-18T16:02:36","deletedAt":"2016-03-18T16:02:47","downloadItems":[{"entityId":18064,"entityType":"datafile","id":2},{"entityId":18061,"entityType":"datafile","id":3}],"email":"","facilityName":"test","fileName":"test_2016-3-18_16-05-59","id":2,"isDeleted":false,"isTwoLevel":false,"preparedId":"6d3aaca5-da9f-4e6a-922d-eceeefcc07e0","status":"COMPLETE","size":324675,"transport":"https","userName":"simple/root"}]
     */
    @GET
    @Path("/downloads")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getDownloads(
        @QueryParam("facilityName") String facilityName,
        @QueryParam("sessionId") String sessionId,
        @QueryParam("queryOffset") String queryOffset)
        throws TopcatException, MalformedURLException, ParseException {

        String icatUrl = getIcatUrl( facilityName );
        onlyAllowAdmin(icatUrl, sessionId);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("queryOffset", queryOffset);

        List<Download> downloads = new ArrayList<Download>();
        downloads = downloadRepository.getDownloads(params);

        return Response.ok().entity(new GenericEntity<List<Download>>(downloads){}).build();
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

        String icatUrl = getIcatUrl( facilityName );
        onlyAllowAdmin(icatUrl, sessionId);

        Download download = downloadRepository.getDownload(id);

        if(download == null){
            throw new NotFoundException("could not find download");
        }

        download.setStatus(DownloadStatus.valueOf(value));
        if(value.equals("COMPLETE")){
            download.setCompletedAt(new Date());
        }

        downloadRepository.save(download);

        return Response.ok().build();
    }
    
    /**
     * Sets whether or not a download is deleted.
     *
     * @summary deleteDownload
     *
	 * @param facilityName
	 *            a facility name - properties must map this to a url to a valid ICAT REST api.
     * 
     * @param sessionId a valid session id which takes the form <code>0d9a3706-80d4-4d29-9ff3-4d65d4308a24</code> 
     *
     * @param id the download id in the database.
     *
     * @param value either true or false.
     *
     * @throws MalformedURLException if facilityName is invalid.
     *
     * @throws ParseException if a JPQL query is malformed.
     * 
     * @throws TopcatException if anything else goes wrong.
     */
    @PUT
    @Path("/download/{id}/isDeleted")
    @Produces({MediaType.APPLICATION_JSON})
    public Response deleteDownload(
        @PathParam("id") Long id,
        @FormParam("facilityName") String facilityName,
        @FormParam("sessionId") String sessionId,
        @FormParam("value") Boolean value)
        throws TopcatException, MalformedURLException, ParseException {

        String icatUrl = getIcatUrl( facilityName );
        onlyAllowAdmin(icatUrl, sessionId);

        Download download = downloadRepository.getDownload(id);

        if(download == null){
            throw new NotFoundException("could not find download");
        }

        download.setIsDeleted(value);
        if(value){
            download.setDeletedAt(new Date());
        }

        downloadRepository.save(download);

        return Response.ok().build();
    }

    /**
     * Sets the specified downloadType's status.
     * 
     * @param downloadType - name of the download type (as configured in topcat.json - downloadTransportTypes[].type)
     * @param facilityName - name of the facility
     * @param sessionId - a valid ICAT sessionId
     * @param disabled - boolean flag - use True to disable, False to enable
     * @param message - message to display to users to indicate that/why this download type is disabled
     * @throws MalformedURLException if facilityName is invalid
     * @throws TopcatException if anything else goes wrong
     */
    @PUT
    @Path("/downloadType/{type}/status")
    @Produces({MediaType.APPLICATION_JSON})
    public Response setDownloadTypeStatus(
        @PathParam("type") String downloadTypeName,
        @FormParam("facilityName") String facilityName,
        @FormParam("sessionId") String sessionId,
        @FormParam("disabled") Boolean disabled,
        @FormParam("message") String message) 
        throws MalformedURLException, TopcatException
        {

        String icatUrl = getIcatUrl( facilityName );
        onlyAllowAdmin(icatUrl, sessionId);
        
        // Update existing entry, or create a new one
        DownloadType downloadType = downloadTypeRepository.getDownloadType(facilityName, downloadTypeName);
        if( downloadType == null ) {
        	downloadType = new DownloadType();
        	downloadType.setFacilityName(facilityName);
        	downloadType.setDownloadType(downloadTypeName);
        }
        downloadType.setDisabled(disabled);
        downloadType.setMessage(message);
        
        downloadTypeRepository.save(downloadType);
        
        return Response.ok().build();
     }

   /**
     * Removes any cached value for the size of the specified entity.
     *
     * @summary clearCachedSize
     *
     * @param entityType the type of the entity: "investigation", "dataset" or "datafile".
     * 
     * @param id the entity id.
     *
	 * @param facilityName
	 *            a facility name - properties must map this to a url to a valid ICAT REST api.
     * 
     * @param sessionId a valid session id which takes the form <code>0d9a3706-80d4-4d29-9ff3-4d65d4308a24</code> 
     *
     * @throws MalformedURLException if facilityName is invalid.
     *
     * @throws TopcatException if anything else goes wrong.
     */
    @DELETE
    @Path("/clearSize/{entityType}/{id}")
    @Produces({MediaType.APPLICATION_JSON})
    public Response clearCachedSize(
        @PathParam("entityType") String entityType,
        @PathParam("id") Long id,
        @QueryParam("facilityName") String facilityName,
        @QueryParam("sessionId") String sessionId)
        throws TopcatException, MalformedURLException {
    	
        String icatUrl = getIcatUrl( facilityName );
        onlyAllowAdmin(icatUrl, sessionId);

        String key = "getSize:" + entityType + ":" + id;
        cacheRepository.remove(key);
        return Response.ok().build();
    }
    
    private void onlyAllowAdmin(String icatUrl, String sessionId) throws TopcatException, MalformedURLException {
        IcatClient icatClient = new IcatClient(icatUrl, sessionId);

        if(icatUrl == null || sessionId == null || !icatClient.isAdmin()){
            throw new ForbiddenException("please provide a valid facilityName and sessionId");
        }
    }


    /**
     * Stores a configuration variable.
     *
     * @summary getConfVar
     *
	 * @param facilityName
	 *            a facility name - properties must map this to a url to a valid ICAT REST api.
     * 
     * @param sessionId a valid session id which takes the form <code>0d9a3706-80d4-4d29-9ff3-4d65d4308a24</code> 
     *
     * @param value a string containing the configuration variable value.
     *
     * @throws TopcatException if anything else goes wrong.
     */
    @PUT
    @Path("/confVars/{name}")
    @Produces({MediaType.APPLICATION_JSON})
    public Response setConfVar(
        @PathParam("name") String name,
        @FormParam("facilityName") String facilityName,
        @FormParam("sessionId") String sessionId,
        @FormParam("value") String value)
        throws TopcatException, MalformedURLException {

        String icatUrl = getIcatUrl( facilityName );
        onlyAllowAdmin(icatUrl, sessionId);

        ConfVar confVar = confVarRepository.getConfVar(name);
        if(confVar == null){
            confVar = new ConfVar();
            confVar.setName(name);
        }

        confVar.setValue(value);

        confVarRepository.save(confVar);

        return Response.ok().build();
    }

	private String getIcatUrl( String facilityName ) throws BadRequestException{
		// Pass nulls through - subsequent code may want to handle them
		if( facilityName == null){
			logger.warn("AdminResource.getIcatUrl: facilityName is null. May be a request using the old API.");
			return null;
		}
		try {
			return FacilityMap.getInstance().getIcatUrl(facilityName);
		} catch (InternalException ie){
			throw new BadRequestException( ie.getMessage() );
		}
	}

}
