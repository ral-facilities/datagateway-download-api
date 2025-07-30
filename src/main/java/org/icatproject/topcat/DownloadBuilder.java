package org.icatproject.topcat;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.icatproject.topcat.IcatClient.DatafilesResponse;
import org.icatproject.topcat.domain.Download;
import org.icatproject.topcat.domain.DownloadItem;
import org.icatproject.topcat.domain.EntityType;
import org.icatproject.topcat.exceptions.BadRequestException;
import org.icatproject.topcat.exceptions.InternalException;
import org.icatproject.topcat.exceptions.NotFoundException;
import org.icatproject.topcat.exceptions.TopcatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonValue;

public class DownloadBuilder {

	private static final Logger logger = LoggerFactory.getLogger(DownloadBuilder.class);

	private long queueVisitMaxPartFileCount;
	private long queueFilesMaxFileCount;

    private String sessionId;
    private String email;
    private String userName;
    private String fullName;
	private int priority = 0;
    private IcatClient icatClient;

    public String transport;
    public String facilityName;
    public String fileName;
	public final List<Download> downloads = new ArrayList<Download>();

    public DownloadBuilder(String sessionId, String email, String fileName, String transport, String facilityName) throws TopcatException {
		Properties properties = Properties.getInstance();
		queueVisitMaxPartFileCount = Long.valueOf(properties.getProperty("queue.visit.maxPartFileCount", "10000"));
		queueFilesMaxFileCount = Long.valueOf(properties.getProperty("queue.files.maxFileCount", "10000"));

        this.sessionId = sessionId;
        this.fileName = fileName;
		this.transport = validateTransport(transport);
		this.facilityName = validateFacilityName(facilityName);
		this.email = validateEmail(transport, email);
		String icatUrl = getIcatUrl(this.facilityName);

		icatClient = new IcatClient(icatUrl, sessionId);

		userName = icatClient.getUserName();
		fullName = icatClient.getFullName();
		priority = icatClient.getQueuePriority(userName);
		icatClient.checkQueueAllowed(userName);
    }

	/**
	 * Validate that the submitted transport mechanism is not null or empty.
	 * 
	 * @param transport Transport mechanism to use
	 * @return transport
	 * @throws BadRequestException if null or empty
	 */
	public static String validateTransport(String transport) throws BadRequestException {
		if (transport == null || transport.trim().isEmpty()) {
			throw new BadRequestException("transport is required");
		}
        return transport;
	}

	/**
	 * Validate that if the submitted facilityName is null, then a default is defined.
	 * 
	 * @param facilityName ICAT Facility.name
	 * @return facilityName or the default facilityName
	 * @throws BadRequestException if null and default not defined
	 */
	public static String validateFacilityName(String facilityName) throws BadRequestException {
		try {
			return FacilityMap.getInstance().validateFacilityName(facilityName);
		} catch (InternalException ie) {
			throw new BadRequestException(ie.getMessage());
		}
	}

	/**
	 * Validate that the submitted email is not null or empty if mail.required is true.
	 * 
	 * @param transport Transport mechanism to use (which may require email)
	 * @param email Users email address, which may be null or empty
	 * @return The original email, or null if it was an empty string
	 * @throws BadRequestException if email null or empty and mail.required is true
	 */
	public static String validateEmail(String transport, String email) throws BadRequestException {
		if(email != null && email.equals("")){
			email = null;
		}

		String emailRequired = Properties.getInstance().getProperty("mail.required." + transport, "false");
		if (Boolean.parseBoolean(emailRequired) && email == null) {
			throw new BadRequestException("email is required for " + transport);
		}

		return email;
	}

	/**
	 * @param facilityName ICAT Facility.name
	 * @return ICAT server url
	 * @throws BadRequestException If facilityName is not recognised
	 */
	public static String getIcatUrl(String facilityName) throws BadRequestException{
		try {
			return FacilityMap.getInstance().getIcatUrl(facilityName);
		} catch (InternalException ie) {
			throw new BadRequestException(ie.getMessage());
		}
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
	private Download createDownload() {
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
	 * Adds Datasets and Datafiles from from a DataCollection as DownloadItems.
	 * Investigations will be split into constituent Datasets.
	 * 
	 * @param dataCollectionId      ICAT DataCollection.id
	 * @throws TopcatException if querying ICAT fails
	 */
    public void extractDataCollection(Long dataCollectionId) throws TopcatException {

		logger.info("extractDataCollection called for {}", dataCollectionId);
		if (dataCollectionId == null || dataCollectionId < 1) {
			throw new BadRequestException("Valid dataCollectionId must be provided");
		}

		if (fileName == null) {
			fileName = facilityName + "_DataCollection" + dataCollectionId;
		}

		JsonArrayBuilder datasetsBuilder = Json.createArrayBuilder();
		JsonArray datafiles = JsonArray.EMPTY_JSON_ARRAY;
		for (JsonValue dataset : icatClient.getDataCollectionDatasets(dataCollectionId)) {
			datasetsBuilder.add(dataset);
		}
		datafiles = icatClient.getDataCollectionDatafiles(dataCollectionId);
		JsonArray datasets = datasetsBuilder.build();

		if (datasets.size() == 0 && datafiles.size() == 0) {
			throw new NotFoundException("No data found for DataCollection " + dataCollectionId);
		}

		buildDownloads(datasets, datafiles);
    }

	/**
	 * Adds Datasets from an Investigation as DownloadItems.
	 * 
	 * @param visitId ICAT Investigation.visitId
	 * @throws TopcatException if querying ICAT fails
	 */
    public void extractVisit(String visitId) throws TopcatException {
		logger.info("extractVisit called for {}", visitId);

		if (visitId == null || visitId.equals("")) {
			throw new BadRequestException("visitId must be provided");
		}

		if (fileName == null) {
			fileName = facilityName + "_" + visitId;
		}

		JsonArray datasets = icatClient.getDatasets(visitId);
		if (datasets.size() == 0) {
			throw new NotFoundException("No Datasets found for " + visitId);
		}

		buildDownloads(datasets, JsonArray.EMPTY_JSON_ARRAY);
    }

	/**
	 * Adds Datafiles as DownloadItems.
	 * 
	 * @param files List of ICAT Datafile.locations
	 * @return DatafilesResponse object representing the found and missing Datafiles
	 * @throws TopcatException if querying ICAT fails
	 * @throws UnsupportedEncodingException if query encoding fails
	 */
    public DatafilesResponse extractLocations(List<String> files) throws TopcatException, UnsupportedEncodingException {
		if (files == null || files.size() == 0) {
			throw new BadRequestException("At least one Datafile.location required");
		} else if (files.size() > queueFilesMaxFileCount) {
			throw new BadRequestException("Limit of " + queueFilesMaxFileCount + " files exceeded");
		}

		logger.info("extractLocations called for {} files", files.size());

		if (fileName == null) {
			fileName = facilityName + "_files";
		}
	
		DatafilesResponse response = icatClient.getDatafiles(files);
		if (response.ids.size() == 0) {
			throw new NotFoundException("No Datafiles found");
		}

		List<DownloadItem> downloadItems = new ArrayList<>();
		Download download = createDownload();
		for (long datafileId : response.ids) {
			DownloadItem downloadItem = createDownloadItem(download, datafileId, EntityType.datafile);
			downloadItems.add(downloadItem);
		}
		download.setDownloadItems(downloadItems);
		download.setSize(response.totalSize);
		download.setPriority(priority);
		downloads.add(download);

		return response;
    }

	/**
	 * Add Downloads to this.downloads containing datasets and datafiles as
	 * DownloadItems, so that none exceeds the configured part limit.
	 * 
	 * @param datasets JsonArray of [dataset.id, dataset.fileCount, dataset.fileSize]
	 * @param datafiles JsonArray of [datafile.id, datafile.fileSize]
	 * @throws TopcatException if size calculation fails
	 */
	private void buildDownloads(JsonArray datasets, JsonArray datafiles) throws TopcatException {
		long downloadFileCount = 0L;
		long downloadFileSize = 0L;
		List<DownloadItem> downloadItems = new ArrayList<DownloadItem>();
		Download newDownload = createDownload();

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
				newDownload = createDownload();
			}

			DownloadItem downloadItem = createDownloadItem(newDownload, datasetId, EntityType.dataset);
			downloadItems.add(downloadItem);
			downloadFileCount += datasetFileCount;
			downloadFileSize += datasetFileSize;
		}
		for (JsonValue datafile : datafiles) {
			JsonArray datafileArray = datafile.asJsonArray();
			long datafileId = datafileArray.getJsonNumber(0).longValueExact();
			long datafileSize = datafileArray.getJsonNumber(1).longValueExact();

			if (downloadFileCount >= queueVisitMaxPartFileCount) {
				newDownload.setDownloadItems(downloadItems);
				newDownload.setSize(downloadFileSize);
				downloads.add(newDownload);

				downloadFileCount = 0L;
				downloadFileSize = 0L;
				downloadItems = new ArrayList<DownloadItem>();
				newDownload = createDownload();
			}

			DownloadItem downloadItem = createDownloadItem(newDownload, datafileId, EntityType.datafile);
			downloadItems.add(downloadItem);
			downloadFileCount += 1L;
			downloadFileSize += datafileSize;
		}
		newDownload.setDownloadItems(downloadItems);
		newDownload.setSize(downloadFileSize);
		downloads.add(newDownload);
	}
}
