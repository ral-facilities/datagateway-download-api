package org.icatproject.topcat;

import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import jakarta.ejb.EJB;
import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import org.icatproject.topcat.httpclient.HttpClient;
import org.icatproject.topcat.domain.Cart;
import org.icatproject.topcat.domain.Download;
import org.icatproject.topcat.domain.DownloadStatus;
import org.icatproject.topcat.exceptions.BadRequestException;
import org.icatproject.topcat.exceptions.ForbiddenException;
import org.icatproject.topcat.exceptions.NotFoundException;
import org.icatproject.topcat.exceptions.TopcatException;
import org.icatproject.topcat.repository.CacheRepository;
import org.icatproject.topcat.repository.CartRepository;
import org.icatproject.topcat.repository.DownloadRepository;
import org.icatproject.topcat.repository.DownloadTypeRepository;
import org.icatproject.topcat.web.rest.UserResource;

@ArquillianTest
public class UserResourceTest {

	/*
	 * Of course, these are not unit tests, but use an embedded container and a
	 * local ICAT/IDS which we assume to be populated appropriately.
	 */

	@Deployment
	public static JavaArchive createDeployment() {
		return ShrinkWrap.create(JavaArchive.class)
				.addClasses(UserResource.class, CacheRepository.class, DownloadRepository.class,
						DownloadTypeRepository.class, CartRepository.class)
				.addPackages(true, "org.icatproject.topcat.domain", "org.icatproject.topcat.exceptions")
				.addAsResource("META-INF/persistence.xml")
				.addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
	}

	@EJB
	private DownloadRepository downloadRepository;

	@EJB
	private DownloadTypeRepository downloadTypeRepository;

	@EJB
	private CartRepository cartRepository;

	@EJB
	private CacheRepository cacheRepository;

	@Inject
	private UserResource userResource;

	private static String sessionId;

	@BeforeAll
	public static void beforeAll() {
		TestHelpers.installTrustManager();
	}

	@BeforeEach
	public void setup() throws Exception {
		HttpClient httpClient = new HttpClient("https://localhost:8181/icat");
		String loginData = "json=" + URLEncoder.encode(
				"{\"plugin\":\"simple\", \"credentials\":[{\"username\":\"root\"}, {\"password\":\"pw\"}]}", "UTF8");
		String response = httpClient.post("session", new HashMap<String, String>(), loginData).toString();
		sessionId = Utils.parseJsonObject(response).getString("sessionId");
	}

	@Test
	public void testLogin() throws Exception {
		String loginResponseString = userResource.login(null, "root", "pw", null);
		JsonObject loginResponseObject = Utils.parseJsonObject(loginResponseString);

		assertEquals(1, loginResponseObject.keySet().size(), loginResponseObject.toString());
		assertTrue(loginResponseObject.containsKey("sessionId"));
		// Will throw if not a UUID
		UUID.fromString(loginResponseObject.getString("sessionId"));
	}

	@Test
	public void testLoginFailure() throws Exception {
		assertThrows(ForbiddenException.class, () -> userResource.login(null, "root", "guess", null));
	}

	@Test
	public void testGetSize() throws Exception {
		String facilityName = "LILS";
		String entityType = "investigation";
		IcatClient icatClient = new IcatClient("https://localhost:8181", sessionId);
		JsonObject investigation = icatClient.getEntity(entityType);
		long entityId = investigation.getInt("id");

		Response response = userResource.getSize(facilityName, sessionId, entityType, entityId);

		// Actual size value for investigation id=1 discovered by trial and error!
		// assertEquals((long) 155062810,
		// Long.parseLong(response.getEntity().toString()));
		// Possibly more robust:
		assertTrue(Long.parseLong(response.getEntity().toString()) > (long) 0);
	}

	@Test
	public void testCart() throws Exception {
		String facilityName = "LILS";
		IcatClient icatClient = new IcatClient("https://localhost:8181", sessionId);
		JsonObject dataset = icatClient.getEntity("dataset");
		long entityId = dataset.getInt("id");

		Response response;

		// ENSURE that the cart is empty initially,
		// albeit by using an undocumented feature of the API!

		response = userResource.deleteCartItems(facilityName, sessionId, "*");
		assertEquals(200, response.getStatus());

		// Now the cart ought to be empty

		response = userResource.getCart(facilityName, sessionId);
		assertEquals(200, response.getStatus());
		assertEquals(0, getCartSize(response));

		// If the cart wasn't empty initially, we won't reach this point, so won't
		// "pollute" a non-empty cart
		// We ought to update the Cart DB directly, but let's assume that addCartItems()
		// works correctly...
		// We assume that there is a dataset with id = 1, and that simple/root can see
		// it.

		response = userResource.addCartItems(facilityName, sessionId, "dataset " + entityId, false);
		assertEquals(200, response.getStatus());

		response = userResource.getCart(facilityName, sessionId);
		assertEquals(200, response.getStatus());
		assertEquals(1, getCartSize(response));

		// Now we need to remove the cart item again;
		// Again, this ought to be done directly, rather than using the methods we
		// should be testing independently!

		response = userResource.deleteCartItems(facilityName, sessionId, "dataset " + entityId);
		assertEquals(200, response.getStatus());
		assertEquals(0, getCartSize(response));
	}

	@Test
	public void testSubmitCart() throws Exception {
		String facilityName = "LILS";
		Response response;
		JsonObject json;
		List<Download> downloads;
		IcatClient icatClient = new IcatClient("https://localhost:8181", sessionId);
		JsonObject dataset = icatClient.getEntity("dataset");
		long entityId = dataset.getInt("id");

		// Get the initial state of the downloads - may not be empty
		response = userResource.getDownloads(facilityName, sessionId, null);
		assertEquals(200, response.getStatus());

		downloads = (List<Download>) response.getEntity();
		int initialDownloadsSize = downloads.size();

		// TEST logging
		System.out.println("DEBUG testSubmitCart: initial downloads size: " + initialDownloadsSize);

		// Put something into the Cart, so we have something to submit
		response = userResource.addCartItems(facilityName, sessionId, "dataset " + entityId, false);
		assertEquals(200, response.getStatus());

		// Now submit it
		String transport = "http";
		String email = "";
		String fileName = "dataset-1.zip";
		String zipType = "ZIP";
		response = userResource.submitCart(facilityName, sessionId, transport, email, fileName, zipType);
		assertEquals(200, response.getStatus());
		json = Utils.parseJsonObject(response.getEntity().toString());

		// The returned cart should be empty
		assertEquals(0, json.getJsonArray("cartItems").size());

		// and the downloadId should be positive
		Long downloadId = json.getJsonNumber("downloadId").longValue();
		assertTrue(downloadId > 0);

		// Now, there should be one download, whose downloadId matches
		response = userResource.getDownloads(facilityName, sessionId, null);
		assertEquals(200, response.getStatus());

		// Doesn't parse as JSON, try a direct cast

		downloads = (List<Download>) response.getEntity();

		// In a clean world, we could do this:
		//
		// assertEquals(1, downloads.size());
		// assertEquals( downloadId, downloads.get(0).getId() );
		//
		// but we can't assume there were no other downloads in the list, so instead:

		assertEquals(initialDownloadsSize + 1, downloads.size());

		Download newDownload = findDownload(downloads, downloadId);
		assertNotNull(newDownload);
		assertEquals(facilityName, newDownload.getFacilityName());
		assertEquals("simple/root", newDownload.getUserName());
		assertEquals(transport, newDownload.getTransport());
		// Email is slightly fiddly:
		if (email.equals("")) {
			assertNull(newDownload.getEmail());
		} else {
			assertEquals(email, newDownload.getEmail());
		}
		assertEquals(fileName, newDownload.getFileName());
		assertFalse(newDownload.getIsDeleted());

		// Next, change the download status. Must be different from the current status!
		String downloadStatus = "EXPIRED";
		if (newDownload.getStatus().equals(DownloadStatus.valueOf(downloadStatus))) {
			downloadStatus = "PAUSED";
		}
		// We cannot modify the status of a download without a prepared id
		// Using downloadRepository will create a new Download with a different id,
		// so remove the old one without the preparedId.
		newDownload.setPreparedId("preparedId");
		Download preparedDownload = downloadRepository.save(newDownload);
		downloadRepository.removeDownload(downloadId);
		downloadId = preparedDownload.getId();
		response = userResource.setDownloadStatus(downloadId, facilityName, sessionId, downloadStatus);
		assertEquals(200, response.getStatus());

		// and test that the new status has been set

		response = userResource.getDownloads(facilityName, sessionId, null);
		assertEquals(200, response.getStatus());
		downloads = (List<Download>) response.getEntity();

		newDownload = findDownload(downloads, downloadId);

		// To be thorough, we ought to check that ONLY the status field has changed. Not
		// going to!
		assertEquals(DownloadStatus.valueOf(downloadStatus), newDownload.getStatus());

		// Now flag the download as deleted

		response = userResource.deleteDownload(downloadId, facilityName, sessionId, true);
		assertEquals(200, response.getStatus());

		// and check that it has worked (again, not bothering to check that nothing else
		// has changed)

		response = userResource.getDownloads(facilityName, sessionId, null);
		assertEquals(200, response.getStatus());
		downloads = (List<Download>) response.getEntity();

		newDownload = findDownload(downloads, downloadId);
		assertTrue(newDownload.getIsDeleted());
	}

	@Test
	public void testQueueVisitId() throws Exception {
		System.out.println("DEBUG testQueueVisitId");
		List<Long> downloadIds = new ArrayList<>();
		try {
			String transport = "http";
			String email = "";
			String visitId = "Proposal 0 - 0 0";
			Response response = userResource.queueVisitId(null, sessionId, transport, null, email, visitId);
			assertEquals(200, response.getStatus());
	
			JsonArray downloadIdsArray = Utils.parseJsonArray(response.getEntity().toString());
			assertEquals(3, downloadIdsArray.size());
			long part = 1;
			for (JsonNumber downloadIdJson : downloadIdsArray.getValuesAs(JsonNumber.class)) {
				long downloadId = downloadIdJson.longValueExact();
				downloadIds.add(downloadId);
			}
			for (long downloadId : downloadIds) {
				Download download = downloadRepository.getDownload(downloadId);
				assertNull(download.getPreparedId());
				assertEquals(DownloadStatus.QUEUED, download.getStatus());
				assertEquals(0, download.getInvestigationIds().size());
				assertEquals(1, download.getDatasetIds().size());
				assertEquals(0, download.getDatafileIds().size());
				assertEquals("LILS_Proposal 0 - 0 0_part_" + part + "_of_3", download.getFileName());
				assertEquals(transport, download.getTransport());
				assertEquals("simple/root", download.getUserName());
				assertEquals("simple/root", download.getFullName());
				assertEquals("", download.getEmail());
				assertNotEquals(0L, download.getSize());
				part += 1;
			}
		} finally {
			for (long downloadId : downloadIds) {
				downloadRepository.removeDownload(downloadId);
			}
		}
	}

	@Test
	public void testQueueVisitIdBadRequest() throws Exception {
		System.out.println("DEBUG testQueueVisitIdBadRequest");
		String facilityName = "LILS";
		String transport = "http";
		String email = "";
		Executable runnable = () -> userResource.queueVisitId(facilityName, sessionId, transport, null, email, null);
		Throwable throwable = assertThrows(BadRequestException.class, runnable);
		assertEquals("(400) : visitId must be provided", throwable.getMessage());

		runnable = () -> userResource.queueVisitId(facilityName, sessionId, transport, null, email, "");
		throwable = assertThrows(BadRequestException.class, runnable);
		assertEquals("(400) : visitId must be provided", throwable.getMessage());
	}

	@Test
	public void testQueueVisitIdNotFound() throws Exception {
		System.out.println("DEBUG testQueueVisitIdBadRequest");
		String facilityName = "LILS";
		String transport = "http";
		String email = "";
		String visitId = "test";
		Executable runnable = () -> userResource.queueVisitId(facilityName, sessionId, transport, null, email, visitId);
		Throwable throwable = assertThrows(NotFoundException.class, runnable);
		assertEquals("(404) : No Datasets found for " + visitId, throwable.getMessage());
	}

	@Test
	public void testQueueFiles() throws Exception {
		System.out.println("DEBUG testQueueFiles");
		Long downloadId = null;
		try {
			String transport = "http";
			String email = "";
			String file = "abcdefghijklmnopqrstuvwxyz";
			IcatClient icatClient = new IcatClient("https://localhost:8181", sessionId);
			List<JsonObject> datafiles = icatClient.getEntities("datafile", 2L);
			List<String> files = new ArrayList<>();
			files.add(file);
			for (JsonObject datafile : datafiles) {
				files.add(datafile.getString("location"));
			}
			Response response = userResource.queueFiles(null, sessionId, transport, null, email, files);
			assertEquals(200, response.getStatus());
			JsonObject responseObject = Utils.parseJsonObject(response.getEntity().toString());
			downloadId = responseObject.getJsonNumber("downloadId").longValueExact();
			JsonArray missingArray = responseObject.getJsonArray("notFound");
			assertEquals(1, missingArray.size());
			assertEquals(file, missingArray.getString(0));
			Download download = downloadRepository.getDownload(downloadId);
			assertNull(download.getPreparedId());
			assertEquals(DownloadStatus.QUEUED, download.getStatus());
			assertEquals(0, download.getInvestigationIds().size());
			assertEquals(0, download.getDatasetIds().size());
			assertEquals(2, download.getDatafileIds().size());
			assertEquals("LILS_files", download.getFileName());
			assertEquals(transport, download.getTransport());
			assertEquals("simple/root", download.getUserName());
			assertEquals("simple/root", download.getFullName());
			assertEquals("", download.getEmail());
			assertNotEquals(0L, download.getSize());
		} finally {
			if (downloadId != null) {
				downloadRepository.removeDownload(downloadId);
			}
		}
	}

	@Test
	public void testQueueFilesBadRequestEmpty() throws Exception {
		System.out.println("DEBUG testQueueFilesBadRequestEmpty");
		String facilityName = "LILS";
		String transport = "http";
		String email = "";
		Executable runnable = () -> userResource.queueFiles(facilityName, sessionId, transport, null, email, null);
		Throwable throwable = assertThrows(BadRequestException.class, runnable);
		assertEquals("(400) : At least one Datafile.location required", throwable.getMessage());

		runnable = () -> userResource.queueFiles(facilityName, sessionId, transport, null, email, new ArrayList<>());
		throwable = assertThrows(BadRequestException.class, runnable);
		assertEquals("(400) : At least one Datafile.location required", throwable.getMessage());
	}

	@Test
	public void testQueueFilesBadRequestTooMany() throws Exception {
		System.out.println("DEBUG testQueueFilesBadRequestTooMany");
		String facilityName = "LILS";
		String transport = "http";
		String email = "";
		List<String> files = List.of("1", "2", "3", "4");
		Executable runnable = () -> userResource.queueFiles(facilityName, sessionId, transport, null, email, files);
		Throwable throwable = assertThrows(BadRequestException.class, runnable);
		assertEquals("(400) : Limit of 3 files exceeded", throwable.getMessage());
	}

	@Test
	public void testQueueFilesNotFound() throws Exception {
		System.out.println("DEBUG testQueueFilesNotFound");
		String facilityName = "LILS";
		String transport = "http";
		String email = "";
		Executable runnable = () -> userResource.queueFiles(facilityName, sessionId, transport, null, email, List.of("test"));
		Throwable throwable = assertThrows(NotFoundException.class, runnable);
		assertEquals("(404) : No Datafiles found", throwable.getMessage());
	}

	@Test
	public void testQueueAllowed() throws Exception {
		System.out.println("DEBUG testQueueAllowed");

		String facilityName = "LILS";
		Response response = userResource.queueAllowed(sessionId, facilityName);
		assertEquals(200, response.getStatus());
		assertEquals(true, response.getEntity());
	}

	@Test
	public void testSetDownloadStatus() throws Exception {
		Long downloadId = null;
		try {
			Download testDownload = new Download();
			String facilityName = "LILS";
			testDownload.setFacilityName(facilityName);
			testDownload.setSessionId(sessionId);
			testDownload.setStatus(DownloadStatus.QUEUED);
			testDownload.setIsDeleted(false);
			testDownload.setUserName("simple/root");
			testDownload.setFileName("testFile.txt");
			testDownload.setTransport("http");
			downloadRepository.save(testDownload);
			downloadId = testDownload.getId();
	
			ForbiddenException exception = assertThrows(ForbiddenException.class, () -> {
				userResource.setDownloadStatus(testDownload.getId(), facilityName, sessionId, DownloadStatus.RESTORING.toString());
			});
			assertEquals("(403) : Cannot modify status of a QUEUED Download", exception.getMessage());
	
			Response response = userResource.getDownloads(facilityName, sessionId, null);
			assertEquals(200, response.getStatus());
			List<Download> downloads = (List<Download>) response.getEntity();
	
			Download unmodifiedDownload = findDownload(downloads, downloadId);
			assertEquals(DownloadStatus.QUEUED, unmodifiedDownload.getStatus());
		} finally {
			if (downloadId != null) {
				downloadRepository.removeDownload(downloadId);
			}
		}
	}

	@Test
	public void testGetDownloadTypeStatus() throws Exception {

		String facilityName = "LILS";
		String downloadType = "http";
		Response response;
		JsonObject json;

		response = userResource.getDownloadTypeStatus(downloadType, facilityName, sessionId);
		assertEquals(200, response.getStatus());

		json = Utils.parseJsonObject(response.getEntity().toString());
		assertTrue(json.containsKey("disabled"));
		assertTrue(json.containsKey("message"));

		// There's not much we can assume about the actual status;
		// but should test that the fields contain the correct types

		try {
			Boolean disabled = json.getBoolean("disabled");
			String message = json.getString("message");
		} catch (Exception e) {
			fail("One or both fields are not of the correct type: " + e.getMessage());
		}
	}

	@Test
	public void testGetDownloadStatusesBadRequest() throws MalformedURLException, TopcatException, ParseException {
		Executable runnable = () -> userResource.getDownloadStatuses("LILS", sessionId, new ArrayList<>());
		assertThrows(BadRequestException.class, runnable);
	}

	@Test
	public void testGetDownloadNotFound() throws MalformedURLException, TopcatException, ParseException {
		List<Long> downloadIds = new ArrayList<>();
		try {
			Download download = TestHelpers.createDummyDownload("simple/notroot", null, "http", true,
					DownloadStatus.COMPLETE, false, downloadRepository);

			downloadIds.add(download.getId());
			Executable runnable = () -> userResource.getDownloadStatuses("LILS", sessionId, downloadIds);
			assertThrows(NotFoundException.class, runnable);
		} finally {
			downloadIds.forEach(downloadId -> {
				TestHelpers.deleteDummyDownload(downloadId, downloadRepository);
			});
		}
	}

	@Test
	public void testGetDownloadStatuses() throws MalformedURLException, TopcatException, ParseException {
		List<Long> downloadIds = new ArrayList<>();
		try {
			Download download1 = TestHelpers.createDummyDownload("simple/root", null, "http", true,
					DownloadStatus.COMPLETE, false, downloadRepository);
			Download download2 = TestHelpers.createDummyDownload("simple/root", null, "http", true,
					DownloadStatus.RESTORING, false, downloadRepository);

			downloadIds.add(download1.getId());
			downloadIds.add(download2.getId());
			Response response = userResource.getDownloadStatuses("LILS", sessionId, downloadIds);
			assertEquals(Arrays.asList(DownloadStatus.COMPLETE, DownloadStatus.RESTORING), response.getEntity());
		} finally {
			downloadIds.forEach(downloadId -> {
				TestHelpers.deleteDummyDownload(downloadId, downloadRepository);
			});
		}
	}

	private int getCartSize(Response response) throws Exception {
		// Trying to write these tests has revealed that UserResource.getSize() is
		// inconsistent!
		// The Response entity returned when the cart is empty cannot be cast to a Cart,
		// but must be parsed as JSON;
		// but the entity returned for a non-empty cart *cannot* be parsed as JSON, but
		// must be cast instead!
		// We can't tell whether or not the cart is empty without trying to read it!
		// Hence this rather ugly and hard-won code...

		int size;

		try {
			// This works for a non-empty cart (but then fails the assertion)
			Cart cart = (Cart) response.getEntity();
			size = cart.getCartItems().size();
			System.out.println("DEBUG: Cast cart worked, size = " + size);

			// Just for completeness' sake, let's see what JSON parsing does in this case:
			try {
				JsonObject json = Utils.parseJsonObject(response.getEntity().toString());
				size = json.getJsonArray("cartItems").size();
				System.out.println("DEBUG: json parsing also worked, size = " + size);
			} catch (Exception e) {
				System.out.println("DEBUG: json parsing failed, when cast size = " + size);
			}

		} catch (Exception e) {

			// This works for an empty cart (but not for a non-empty one)
			System.out.println("DEBUG: Cast cart failed, try json parsing instead");
			JsonObject json = Utils.parseJsonObject(response.getEntity().toString());
			size = json.getJsonArray("cartItems").size();
			System.out.println("DEBUG: json parsing worked, size = " + size);
		}

		return size;
	}

	private Download findDownload(List<Download> downloads, Long downloadId) {

		for (Download download : downloads) {
			if (download.getId().equals(downloadId))
				return download;
		}
		return null;
	}

}
