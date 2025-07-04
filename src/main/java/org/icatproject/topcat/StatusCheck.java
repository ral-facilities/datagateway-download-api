package org.icatproject.topcat;

import java.net.URL;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.ejb.EJB;
import jakarta.ejb.Lock;
import jakarta.ejb.LockType;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.json.JsonObject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.annotation.Resource;

import org.icatproject.topcat.domain.Download;
import org.icatproject.topcat.domain.DownloadStatus;
import org.icatproject.topcat.Properties;
import org.icatproject.topcat.Utils;
import org.icatproject.topcat.repository.*;
import org.icatproject.topcat.IdsClient;
import org.icatproject.topcat.FacilityMap;

import org.icatproject.topcat.exceptions.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.validator.routines.EmailValidator;

import jakarta.mail.Message;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;


@Singleton
public class StatusCheck {

  private static final Logger logger = LoggerFactory.getLogger(StatusCheck.class);
  private Map<Long, Date> lastChecks = new HashMap<Long, Date>();
  private AtomicBoolean busy = new AtomicBoolean(false);

  @PersistenceContext(unitName="topcat")
  EntityManager em;

  @EJB
  private DownloadRepository downloadRepository;

  @Resource(name = "mail/topcat")
  private Session mailSession;

  /**
   * poll thread will be WRITE locked, which is the default behaviour for Singletons
   * All write operations should go in this function, we do not want other WRITE locked
   * threads (e.g. for queuing) to block traditional user cart submissions.
   */
  @Lock(LockType.WRITE)
  @Schedule(hour = "*", minute = "*", second = "*")
  private void poll() {

    // Observation: glassfish may already prevent multiple executions, and may even
    // count the attempt as an error, so it is possible that the use of a semaphore
    // here is redundant.
	  
    if (!busy.compareAndSet(false, true)) {
      return;
    }

    try {
      Properties properties = Properties.getInstance();
      int pollDelay = Integer.valueOf(properties.getProperty("poll.delay", "600"));
      int pollIntervalWait = Integer.valueOf(properties.getProperty("poll.interval.wait", "600"));

      // For testing, separate out the poll body into its own method
      // And allow test configurations to disable scheduled status checks
      if (!Boolean.valueOf(properties.getProperty("test.disableDownloadStatusChecks", "false"))) {
        boolean downloadsUpdated = updateStatuses(pollDelay, pollIntervalWait, null);
        if (!downloadsUpdated) {
          // Only process a Download from the queue if there was no work to do for Cart based Downloads
          int maxActiveDownloads = Integer.valueOf(properties.getProperty("queue.maxActiveDownloads", "1"));
          startQueuedDownload(maxActiveDownloads);
        }
      }

    } catch (Exception e) {
      logger.error(e.getMessage());
    } finally {
      busy.set(false);
    }
  }
  
  /**
   * Update the status of each relevant download.
   * 
   * @param pollDelay         minimum time to wait before initial
   *                          preparation/check
   * @param pollIntervalWait  minimum time between checks
   * @param injectedIdsClient optional (possibly mock) IdsClient
   * @return Whether any Downloads to update were found and prepared
   * @throws Exception
   */
  public boolean updateStatuses(int pollDelay, int pollIntervalWait, IdsClient injectedIdsClient) throws Exception {
	  
    // This method is intended for testing, but we are forced to make it public
    // rather than protected.
    boolean statusesUpdated = false;
    String selectString = "select download from Download download where download.isDeleted != true";
    String notExpiredCondition = "download.status != org.icatproject.topcat.domain.DownloadStatus.EXPIRED";
    String preparingCondition = "download.status = org.icatproject.topcat.domain.DownloadStatus.PREPARING";
    String restoringHttpCondition = "(download.status = org.icatproject.topcat.domain.DownloadStatus.RESTORING and download.transport in ('https','http'))";
    String notEmailSentCondition = "(download.email != null and download.isEmailSent = false)";
    String isActiveCondition = preparingCondition + " or " + restoringHttpCondition + " or " + notEmailSentCondition;
    String queryString = selectString + " and " + notExpiredCondition + " and (" + isActiveCondition + ")";

    TypedQuery<Download> query = em.createQuery(queryString, Download.class);
    List<Download> downloads = query.getResultList();

    if (downloads.size() == 0) {
      return statusesUpdated;
    }

    for (Download download : downloads) {
      Date lastCheck = lastChecks.get(download.getId());
      Date now = new Date();
      long createdSecondsAgo = (now.getTime() - download.getCreatedAt().getTime()) / 1000;
      if (download.getStatus() == DownloadStatus.PREPARING) {
        // If prepareDownload was called previously but caught an exception (other than
        // TopcatException), we should not call it again immediately, but should impose
        // a delay. See issue #462.
        if (lastCheck == null) {
      	  prepareDownload(download, injectedIdsClient);
          statusesUpdated = true;
        } else {
          long lastCheckSecondsAgo = (now.getTime() - lastCheck.getTime()) / 1000;
          if (lastCheckSecondsAgo >= pollIntervalWait) {
         	  prepareDownload(download, injectedIdsClient);
            statusesUpdated = true;
          }
        }
      } else if (download.getPreparedId() != null && createdSecondsAgo >= pollDelay) {
        if (lastCheck == null) {
          performCheck(download, injectedIdsClient);
        } else {
          long lastCheckSecondsAgo = (now.getTime() - lastCheck.getTime()) / 1000;
          if (lastCheckSecondsAgo >= pollIntervalWait) {
            performCheck(download, injectedIdsClient);
          }
        }
      }
    }

    return statusesUpdated;
  }

  private void performCheck(Download download, IdsClient injectedIdsClient) {
    try {
      IdsClient idsClient = injectedIdsClient;
      if( idsClient == null ) {
    	  idsClient = new IdsClient(getDownloadUrl(download.getFacilityName(),download.getTransport()));
      }
      if(!download.getIsEmailSent() && download.getStatus() == DownloadStatus.COMPLETE){
    	  logger.info("Download COMPLETE for " + download.getFileName() + " " + download.getId() + "; checking whether to send email...");
        download.setIsEmailSent(true);
        em.persist(download);
        em.flush();
        lastChecks.remove(download.getId());
        sendDownloadReadyEmail(download);
      } else if(download.getTransport().matches("https|http") && idsClient.isPrepared(download.getPreparedId())){
    	  logger.info("Download (http[s]) for " + download.getFileName() + " " + download.getId() + " is Prepared, so setting COMPLETE and checking email...");
        download.setStatus(DownloadStatus.COMPLETE);
        download.setCompletedAt(new Date());
        download.setIsEmailSent(true);
        em.persist(download);
        em.flush();
        lastChecks.remove(download.getId());
        sendDownloadReadyEmail(download);
      } else {
        lastChecks.put(download.getId(), new Date());
      }
    } catch (IOException e){
    	handleException(download,"performCheck IOException: " + e.toString());
    } catch(NotFoundException e){
    	handleException(download,"performCheck NotFoundException: " + e.getMessage());
    } catch(TopcatException e) {
    	// Note: only expire downloads for TopcatExceptions. See issue #462
    	handleException(download,"performCheck TopcatException: " + e.toString(), true);
    } catch(Exception e){
    	handleException(download,"performCheck Exception: " + e.toString());
    }
  }

  private void sendDownloadReadyEmail(Download download) throws InternalException{
    EmailValidator emailValidator = EmailValidator.getInstance();
    Properties properties = Properties.getInstance();

    if (properties.getProperty("mail.enable", "false").equals("true")) {
      if (download.getEmail() != null && emailValidator.isValid(download.getEmail())) {
        // get fullName if exists
        String userName = download.getUserName();
        String fullName = download.getFullName();
        if (fullName != null && !fullName.trim().isEmpty()) {
          userName = fullName;
        }

        String downloadUrl = getDownloadUrl(download.getFacilityName(),download.getTransport());
        downloadUrl += "/ids/getData?preparedId=" + download.getPreparedId();
        downloadUrl += "&outname=" + download.getFileName();

        Map<String, String> valuesMap = new HashMap<String, String>();
        valuesMap.put("email", download.getEmail());
        valuesMap.put("userName", userName);
        valuesMap.put("facilityName", download.getFacilityName());
        valuesMap.put("preparedId", download.getPreparedId());
        valuesMap.put("downloadUrl", downloadUrl);
        valuesMap.put("fileName", download.getFileName());
        valuesMap.put("size", Utils.bytesToHumanReadable(download.getSize()));

        StringSubstitutor sub = new StringSubstitutor(valuesMap);
        String subject = sub.replace(properties.getProperty("mail.subject", "mail.subject not set in run.properties"));
        String bodyProperty = "mail.body." + download.getTransport();
        String body = sub.replace(properties.getProperty(bodyProperty, bodyProperty + " not set in run.properties"));


        Message message = new MimeMessage(mailSession);
        try {
          message.setSubject(subject);
          message.setText(body);
          message.setRecipients(RecipientType.TO, InternetAddress.parse(download.getEmail()));

          Transport.send(message);

          logger.debug("Email sent to " + download.getEmail());
        } catch (MessagingException e) {
          logger.debug(e.getMessage());
        }

      } else {
        logger.debug("Email not sent. Invalid email " + download.getEmail());
      }
    } else {
      logger.debug("Email not sent. Email not enabled");
    }
  }

  /**
   * Public static method for external calls to prepare a Download.
   * 
   * @param downloadRepository DownloadRepository to save the updated Download
   * @param download           Download to prepare
   * @param sessionId          ICAT sessionId to use, possibly different from
   *                           the one set on the Download if it has expired
   * @param idsClient          Optional (possibly mock) IdsClient
   * @throws TopcatException If prepareData fails
   */
  public static void prepareDownload(DownloadRepository downloadRepository, Download download, String sessionId,
      IdsClient idsClient) throws TopcatException {

    if( idsClient == null ) {
      idsClient = new IdsClient(getDownloadUrl(download.getFacilityName(),download.getTransport()));
    }
    logger.info("Requesting prepareData for Download " + download.getFileName() + " " + download.getId());
    String preparedId = idsClient.prepareData(sessionId, download.getInvestigationIds(), download.getDatasetIds(),
        download.getDatafileIds());
    logger.info("Received preparedId " + preparedId + " for Download " + download.getFileName() + " " + download.getId());
    download.setPreparedId(preparedId);

    if (download.getSize() <= 0) {
      try {
        Long size = idsClient.getSize(sessionId, download.getInvestigationIds(), download.getDatasetIds(),
            download.getDatafileIds());
        download.setSize(size);
      } catch(Exception e) {
        logger.error("prepareDownload: setting size to -1 as getSize threw exception: " + e.getMessage());
        download.setSize(-1);
      }
    }

    if (download.getIsTwoLevel() || !download.getTransport().matches("https|http")) {
      logger.info("Setting Download status RESTORING for " + download.getFileName() + " " + download.getId());
      download.setStatus(DownloadStatus.RESTORING);
    } else {
      logger.info("Setting Download status COMPLETE for " + download.getFileName() + " " + download.getId());
      download.setStatus(DownloadStatus.COMPLETE);
      download.setCompletedAt(new Date());
    }

    downloadRepository.save(download);
  }

  /**
   * Private method for internal calls to prepare a Download with a specific sessionId.
   * Exceptions will be handled if possible, and the Download might be marked as
   * EXPIRED as part of this process.
   * 
   * @param download           Download to prepare
   * @param injectedIdsClient  Optional (possibly mock) IdsClient
   * @param sessionId          ICAT sessionId to use, possibly different from
   *                           the one set on the Download if it has expired
   * @throws Exception If internal exceptions could not be handled
   */
  private void prepareDownload(Download download, IdsClient injectedIdsClient, String sessionId) throws Exception {
    try {
      prepareDownload(downloadRepository, download, sessionId, injectedIdsClient);
    } catch(NotFoundException e){
    	handleException(download, "prepareDownload NotFoundException: " + e.getMessage());
    } catch(TopcatException e) {
    	// Note: only expire downloads for TopcatExceptions. See issue #462
    	handleException(download, "prepareDownload TopcatException: " + e.toString(), true);
    } catch(Exception e){
    	handleException(download, "prepareDownload Exception: " + e.toString());
    }
  }

  /**
   * Private method for internal calls to prepare a Download. Exceptions will
   * be handled if possible, and the Download might be marked as EXPIRED as
   * part of this process.
   * 
   * @param download           Download to prepare
   * @param injectedIdsClient  Optional (possibly mock) IdsClient
   * @throws Exception If internal exceptions could not be handled
   */
  private void prepareDownload(Download download, IdsClient injectedIdsClient) throws Exception {
    prepareDownload(download, injectedIdsClient, download.getSessionId());
  }

  /**
   * Gets a functional sessionId to use for submitting to the queue, logging in if needed.
   * 
   * @param sessionIds   Map from Facility to functional sessionId
   * @param facilityName Name of ICAT Facility to get the sessionId for
   * @return Functional ICAT sessionId
   * @throws Exception If the login fails
   */
  private String getQueueSessionId(Map<String, String> sessionIds, String facilityName)
      throws Exception {
    String sessionId = sessionIds.get(facilityName);
    if (sessionId == null) {
      IcatClient icatClient = new IcatClient(FacilityMap.getInstance().getIcatUrl(facilityName));
      Properties properties = Properties.getInstance();
      String plugin = properties.getProperty("queue.account." + facilityName + ".plugin");
      String username = properties.getProperty("queue.account." + facilityName + ".username");
      String password = properties.getProperty("queue.account." + facilityName + ".password");
      String jsonString = icatClient.login(plugin, username, password);
      JsonObject jsonObject = Utils.parseJsonObject(jsonString);
      sessionId = jsonObject.getString("sessionId");
      sessionIds.put(facilityName, sessionId);
    }
    return sessionId;
  }

  /**
   * Prepares up to one Download which is QUEUED, up to the maxActiveDownloads limit.
   * Downloads will be prepared in order of priority, with all Downloads from
   * Users with a value of 1 being prepared first, then 2 and so on.
   * 
   * @param maxActiveDownloads Limit on the number of concurrent jobs with
   *                           RESTORING status
   * @throws Exception
   */
  public void startQueuedDownload(int maxActiveDownloads) throws Exception {
    if (maxActiveDownloads == 0) {
      logger.trace("Preparing of queued jobs disabled by config, skipping");
      return;
    }

    String selectString = "select download from Download download where download.isDeleted != true";
    String restoringCondition = "download.status = org.icatproject.topcat.domain.DownloadStatus.RESTORING";
    String queuedCondition = "download.status = org.icatproject.topcat.domain.DownloadStatus.QUEUED";

    int availableDownloads = maxActiveDownloads;
    if (maxActiveDownloads > 0) {
      // Work out how many "available" spaces there are by accounting for the active Downloads
      String activeQueryString = selectString + " and " + restoringCondition;
      TypedQuery<Download> activeDownloadsQuery = em.createQuery(activeQueryString, Download.class);
      List<Download> activeDownloads = activeDownloadsQuery.getResultList();
      int activeDownloadsSize = activeDownloads.size();
      if (activeDownloadsSize >= maxActiveDownloads) {
        String format = "More downloads currently RESTORING {} than maxActiveDownloads {}, cannot prepare queued jobs";
        logger.trace(format, activeDownloadsSize, maxActiveDownloads);
        return;
      }
      availableDownloads -= activeDownloadsSize;
    }

    String queuedQueryString = selectString + " and " + queuedCondition;
    queuedQueryString += " order by download.createdAt";
    TypedQuery<Download> queuedDownloadsQuery = em.createQuery(queuedQueryString, Download.class);
    List<Download> queuedDownloads = queuedDownloadsQuery.getResultList();
    int queueSize = queuedDownloads.size();
    if (queueSize == 0) {
      return;
    }

    Map<String, String> sessionIds = new HashMap<>();
    if (maxActiveDownloads <= 0) {
      // No limits on how many to submit
      logger.info("Preparing 1 out of {} queued downloads", queueSize);
      Download queuedDownload = queuedDownloads.get(0);
      queuedDownload.setStatus(DownloadStatus.PREPARING);
      prepareDownload(queuedDownload, null, getQueueSessionId(sessionIds, queuedDownload.getFacilityName()));
    } else {
      logger.info("Preparing 1 out of {} queued downloads as {} spaces available", queueSize, availableDownloads);
      HashMap<Integer, List<Download>> mapping = new HashMap<>();
      for (Download queuedDownload : queuedDownloads) {
        String sessionId = getQueueSessionId(sessionIds, queuedDownload.getFacilityName());
        String icatUrl = FacilityMap.getInstance().getIcatUrl(queuedDownload.getFacilityName());
        IcatClient icatClient = new IcatClient(icatUrl, sessionId);
        int priority = icatClient.getQueuePriority(queuedDownload.getUserName());
        if (priority == 1) {
          // Highest priority, prepare now
          queuedDownload.setStatus(DownloadStatus.PREPARING);
          prepareDownload(queuedDownload, null, sessionId);
          return;
        } else {
          // Lower priority, add to mapping
          mapping.putIfAbsent(priority, new ArrayList<>());
          mapping.get(priority).add(queuedDownload);
        }
      }

      // Get the highest priority encountered
      List<Integer> keyList = new ArrayList<>();
      for (Object key : mapping.keySet().toArray()) {
        keyList.add((Integer) key);
      }
      int priority = Collections.min(keyList);

      // Prepare the first Download at this priority level
      List<Download> downloadList = mapping.get(priority);
      Download download = downloadList.get(0);
      download.setStatus(DownloadStatus.PREPARING);
      prepareDownload(download, null, getQueueSessionId(sessionIds, download.getFacilityName()));
    }
  }

  private void handleException( Download download, String reason, boolean doExpire ) {
    if( doExpire ) {
	      logger.error("Marking download " + download.getId() + " as expired. Reason: " + reason);
	      download.setStatus(DownloadStatus.EXPIRED);
	      em.persist(download);
	      em.flush();
	      lastChecks.remove(download.getId());
	  } else {
		  // Record that we have tried to check (or prepare) this download,
		  // so that updateStatuses should not try again immediately.
		  logger.warn( "Ignoring: " + reason);
		  lastChecks.put(download.getId(), new Date());
	  }
  }
  
  private void handleException( Download download, String reason ) {
	  handleException( download, reason, false );
  }

  private static String getDownloadUrl( String facilityName, String downloadType ) throws InternalException{
      return FacilityMap.getInstance().getDownloadUrl(facilityName, downloadType);
  }
}
