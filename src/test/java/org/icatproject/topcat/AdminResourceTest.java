package org.icatproject.topcat;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ejb.EJB;
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
import org.icatproject.topcat.domain.Download;
import org.icatproject.topcat.domain.DownloadStatus;
import org.icatproject.topcat.domain.DownloadType;
import org.icatproject.topcat.exceptions.ForbiddenException;
import org.icatproject.topcat.repository.CacheRepository;
import org.icatproject.topcat.repository.ConfVarRepository;
import org.icatproject.topcat.repository.DownloadRepository;
import org.icatproject.topcat.repository.DownloadTypeRepository;
import org.icatproject.topcat.web.rest.AdminResource;

@ArquillianTest
public class AdminResourceTest {

	/*
	 * Of course, these are not unit tests, but use an embedded container and a
	 * local ICAT/IDS which we assume to be populated appropriately.
	 */

	@Deployment
	public static JavaArchive createDeployment() {
		return ShrinkWrap.create(JavaArchive.class)
				.addClasses(AdminResource.class, CacheRepository.class, DownloadRepository.class,
						DownloadTypeRepository.class, ConfVarRepository.class)
				.addPackages(true, "org.icatproject.topcat.domain", "org.icatproject.topcat.exceptions")
				.addAsResource("META-INF/persistence.xml")
				.addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
	}

	@EJB
	private DownloadRepository downloadRepository;

	@EJB
	private DownloadTypeRepository downloadTypeRepository;

	@EJB
	private ConfVarRepository confVarRepository;

	@EJB
	private CacheRepository cacheRepository;

	@Inject
	private AdminResource adminResource;

	private static String adminSessionId;
	private static String nonAdminSessionId;

	@BeforeAll
	public static void beforeAll() {
		TestHelpers.installTrustManager();
	}

	@BeforeEach
	public void setup() throws Exception {
		HttpClient httpClient = new HttpClient("https://localhost:8181/icat");

		// Log in as an admin user

		String data = "json=" + URLEncoder.encode(
				"{\"plugin\":\"simple\", \"credentials\":[{\"username\":\"root\"}, {\"password\":\"pw\"}]}", "UTF8");
		String response = httpClient.post("session", new HashMap<String, String>(), data).toString();
		adminSessionId = Utils.parseJsonObject(response).getString("sessionId");

		// Also log in as a non-admin user

		data = "json=" + URLEncoder.encode(
				"{\"plugin\":\"simple\", \"credentials\":[{\"username\":\"icatuser\"}, {\"password\":\"icatuserpw\"}]}",
				"UTF8");
		response = httpClient.post("session", new HashMap<String, String>(), data).toString();
		nonAdminSessionId = Utils.parseJsonObject(response).getString("sessionId");
	}

	@Test
	public void testIsValidSession() throws Exception {
		String facilityName = "LILS";
		Response response;

		// Should be true for an admin user

		response = adminResource.isValidSession(facilityName, adminSessionId);
		assertEquals(200, response.getStatus());
		assertEquals("true", (String) response.getEntity());

		// Should be false for a non-admin (but valid) user

		response = adminResource.isValidSession(facilityName, nonAdminSessionId);
		assertEquals(200, response.getStatus());
		assertEquals("false", (String) response.getEntity());

		// Request should fail for a nonsense sessionId
		response = adminResource.isValidSession(facilityName, "nonsense id");
		// Expected this to raise a BadRequestException, but surprisingly it doesn't
		assertEquals(200, response.getStatus());
		assertEquals("false", (String) response.getEntity());
	}

	@Test
	public void testDownloadAPI() throws Exception {
		String facilityName = "LILS";
		Response response;
		JsonObject json;
		List<Download> downloads;

		// Create a new download for the test
		Download testDownload = new Download();
		try {
			testDownload.setFacilityName(facilityName);
			testDownload.setSessionId(adminSessionId);
			testDownload.setStatus(DownloadStatus.PREPARING);
			testDownload.setIsDeleted(false);
	
			// Other fields that can't be null, but which we (hopefully) don't really need:
			testDownload.setUserName("simple/root");
			testDownload.setFileName("testFile.txt");
			testDownload.setTransport("http");
	
			downloadRepository.save(testDownload);
	
			// Get the current downloads - may not be empty
			response = adminResource.getDownloads(facilityName, adminSessionId, null);
			assertEquals(200, response.getStatus());
	
			downloads = (List<Download>) response.getEntity();
	
			// Check that the result tallies with the DownloadRepository contents
	
			Map<String, Object> params = new HashMap<String, Object>();
			List<Download> repoDownloads = new ArrayList<Download>();
			repoDownloads = downloadRepository.getDownloads(params);
	
			assertEquals(repoDownloads.size(), downloads.size());
			for (Download download : repoDownloads) {
				assertNotNull(findDownload(downloads, download.getId()));
			}
	
			// Next, change the download status. Must be different from the current status!
			String downloadStatus = "EXPIRED";
			if (testDownload.getStatus().equals(DownloadStatus.valueOf(downloadStatus))) {
				downloadStatus = "PAUSED";
			}
	
			response = adminResource.setDownloadStatus(testDownload.getId(), facilityName, adminSessionId, downloadStatus, null);
			assertEquals(200, response.getStatus());
	
			// and test that the new status has been set
	
			response = adminResource.getDownloads(facilityName, adminSessionId, null);
			assertEquals(200, response.getStatus());
			downloads = (List<Download>) response.getEntity();
	
			testDownload = findDownload(downloads, testDownload.getId());
	
			// To be thorough, we ought to check that ONLY the status field has changed. Not
			// going to!
			assertEquals(DownloadStatus.valueOf(downloadStatus), testDownload.getStatus());
	
			// Now toggle the deleted status - may have been deleted already!
			Boolean currentDeleted = testDownload.getIsDeleted();
	
			response = adminResource.deleteDownload(testDownload.getId(), facilityName, adminSessionId, !currentDeleted);
			assertEquals(200, response.getStatus());
	
			// and check that it has worked (again, not bothering to check that nothing else
			// has changed)
	
			response = adminResource.getDownloads(facilityName, adminSessionId, null);
			assertEquals(200, response.getStatus());
			downloads = (List<Download>) response.getEntity();
	
			testDownload = findDownload(downloads, testDownload.getId());
			assertNotEquals(testDownload.getIsDeleted(), currentDeleted);
	
			// Test that getDownloadStatus() etc. produce an error response for a non-admin
			// user
	
			try {
				response = adminResource.getDownloads(facilityName, nonAdminSessionId, null);
				// We should not see the following
				System.out.println("DEBUG: AdminRT.getDownloads response: " + response.getStatus() + ", "
						+ (String) response.getEntity());
				fail("AdminResource.getDownloads did not raise exception for non-admin user");
			} catch (ForbiddenException fe) {
			}
	
			try {
				response = adminResource.setDownloadStatus(testDownload.getId(), facilityName, nonAdminSessionId,
						downloadStatus, null);
				// We should not see the following
				System.out.println("DEBUG: AdminRT.setDownloadStatus response: " + response.getStatus() + ", "
						+ (String) response.getEntity());
				fail("AdminResource.setDownloadStatus did not raise exception for non-admin user");
			} catch (ForbiddenException fe) {
			}
	
			try {
				response = adminResource.deleteDownload(testDownload.getId(), facilityName, nonAdminSessionId,
						!currentDeleted);
				// We should not see the following
				System.out.println("DEBUG: AdminRT.deleteDownload response: " + response.getStatus() + ", "
						+ (String) response.getEntity());
				fail("AdminResource.deleteDownload did not raise exception for non-admin user");
			} catch (ForbiddenException fe) {
			}
		} finally {
			// Remove the test download from the repository
			downloadRepository.removeDownload(testDownload.getId());
		}
	}

	@Test
	public void testSetDownloadStatus() throws Exception {
		Long downloadId = null;
		try {
			Download testDownload = new Download();
			String facilityName = "LILS";
			testDownload.setFacilityName(facilityName);
			testDownload.setSessionId("sessionId");
			testDownload.setStatus(DownloadStatus.QUEUED);
			testDownload.setIsDeleted(false);
			testDownload.setUserName("simple/root");
			testDownload.setFileName("testFile.txt");
			testDownload.setTransport("http");
			downloadRepository.save(testDownload);
			downloadId = testDownload.getId();

			Response response = adminResource.setDownloadStatus(downloadId, facilityName, adminSessionId, DownloadStatus.RESTORING.toString(), null);
			assertEquals(200, response.getStatus());

			response = adminResource.getDownloads(facilityName, adminSessionId, null);
			assertEquals(200, response.getStatus());
			List<Download> downloads = (List<Download>) response.getEntity();

			testDownload = findDownload(downloads, downloadId);
			assertEquals(DownloadStatus.PREPARING, testDownload.getStatus());
			assertEquals(adminSessionId, testDownload.getSessionId());
		} finally {
			if (downloadId != null) {
				downloadRepository.removeDownload(downloadId);
			}
		}
	}

	@Test
	public void testSetDownloadStatusNoCustomDownloadUrl() throws Exception {
		Long downloadId = null;
		try {
			Download testDownload = new Download();
			String facilityName = "LILS";
			testDownload.setFacilityName(facilityName);
			testDownload.setSessionId(adminSessionId);
			testDownload.setStatus(DownloadStatus.QUEUED);
			testDownload.setIsDeleted(false);
			testDownload.setUserName("simple/root");
			testDownload.setFileName("testFile.txt");
			testDownload.setTransport("http");
			downloadRepository.save(testDownload);
			downloadId = testDownload.getId();

			Response response = adminResource.setDownloadStatus(downloadId, facilityName, adminSessionId,
				DownloadStatus.COMPLETE.toString(), null);
			assertEquals(200, response.getStatus());

			response = adminResource.getDownloads(facilityName, adminSessionId, null);
			assertEquals(200, response.getStatus());
			List<Download> downloads = (List<Download>) response.getEntity();

			testDownload = findDownload(downloads, downloadId);
			assertEquals(DownloadStatus.COMPLETE, testDownload.getStatus());
			assertFalse(testDownload.getIsEmailSent());
		} finally {
			if (downloadId != null) {
				downloadRepository.removeDownload(downloadId);
			}
		}
	}

	@Test
	public void testSetDownloadStatusCustomDownloadUrl() throws Exception {
		Long downloadId = null;
		try {
			Download testDownload = new Download();
			String facilityName = "LILS";
			testDownload.setFacilityName(facilityName);
			testDownload.setSessionId(adminSessionId);
			testDownload.setStatus(DownloadStatus.QUEUED);
			testDownload.setIsDeleted(false);
			testDownload.setUserName("simple/root");
			testDownload.setFileName("testFile.txt");
			testDownload.setTransport("http");
			downloadRepository.save(testDownload);
			downloadId = testDownload.getId();

			Response response = adminResource.setDownloadStatus(downloadId, facilityName, adminSessionId,
				DownloadStatus.COMPLETE.toString(), "customDownloadUrl");
			assertEquals(200, response.getStatus());

			response = adminResource.getDownloads(facilityName, adminSessionId, null);
			assertEquals(200, response.getStatus());
			List<Download> downloads = (List<Download>) response.getEntity();

			testDownload = findDownload(downloads, downloadId);
			assertEquals(DownloadStatus.COMPLETE, testDownload.getStatus());
			assertTrue(testDownload.getIsEmailSent());
		} finally {
			if (downloadId != null) {
				downloadRepository.removeDownload(downloadId);
			}
		}
	}

	@Test
	public void testSetDownloadTypeStatus() throws Exception {
		System.out.println("DEBUG testSetDownloadTypeStatus");
		Long typeId = null;
		try {
			String facilityName = "LILS";
			String name = "http";
			Boolean disabled = false;
			String message = "";
			Response response = adminResource.setDownloadTypeStatus(name, facilityName, adminSessionId, disabled, message);
			assertEquals(200, response.getStatus());

			// Now test that it has had the desired result

			DownloadType downloadType = downloadTypeRepository.getDownloadType(facilityName, name);
			assertNotNull(downloadType);
			typeId = downloadType.getId();
			assertFalse(downloadType.getDisabled());
			assertEquals(message, downloadType.getMessage());

			// Test that setDownloadTypeStatus produces an error for non-admin users.

			Executable executable = () -> {
				adminResource.setDownloadTypeStatus(name, facilityName, nonAdminSessionId, disabled, message);
			};
			assertThrows(ForbiddenException.class, executable);

		} finally {
			// Remove the entry we created to avoid interference with other tests
			downloadTypeRepository.remove(typeId);
		}
	}

	@Test
	public void testPrepareDownload() throws Exception {
		Long downloadId = null;
		try {
			Download testDownload = new Download();
			String facilityName = "LILS";
			testDownload.setFacilityName(facilityName);
			testDownload.setSessionId(adminSessionId);
			testDownload.setStatus(DownloadStatus.EXPIRED);
			testDownload.setIsDeleted(true);
			testDownload.setIsTwoLevel(true);
			testDownload.setDeletedAt(new Date());
			testDownload.setUserName("simple/root");
			testDownload.setFileName("testFile.txt");
			testDownload.setTransport("http");
			downloadRepository.save(testDownload);
			downloadId = testDownload.getId();

			Response response = adminResource.prepareDownload(downloadId, facilityName, adminSessionId);
			assertEquals(200, response.getStatus());

			response = adminResource.getDownloads(facilityName, adminSessionId, null);
			assertEquals(200, response.getStatus());
			List<Download> downloads = (List<Download>) response.getEntity();

			testDownload = findDownload(downloads, downloadId);
			assertEquals(DownloadStatus.RESTORING, testDownload.getStatus());
			assertFalse(testDownload.getIsDeleted());
			assertNull(testDownload.getDeletedAt());
		} finally {
			if (downloadId != null) {
				downloadRepository.removeDownload(downloadId);
			}
		}
	}

	@Test
	public void testClearCachedSize() throws Exception {

		String facilityName = "LILS";
		String entityType = "dataset";
		Long id = 3L;
		Long size = 150L;
		String key = "getSize:" + entityType + ":" + id;
		Response response;

		// Set a dummy size (this will overwrite any existing cached value!)

		cacheRepository.put(key, size);

		// Now use the API to clear it
		response = adminResource.clearCachedSize(entityType, id, facilityName, adminSessionId);
		assertEquals(200, response.getStatus());

		// and now it should be gone from the repository
		assertNull(cacheRepository.get(key));

		// Test behaviour for non-admin user
		try {
			response = adminResource.clearCachedSize(entityType, id, facilityName, nonAdminSessionId);
			// We should not see the following
			System.out.println("DEBUG: AdminRT.clearCachedSize response: " + response.getStatus() + ", "
					+ (String) response.getEntity());
			fail("AdminResource.clearCachedSize did not raise exception for non-admin user");
		} catch (ForbiddenException fe) {
		}
	}

	@Test
	public void testSetConfVar() throws Exception {

		String facilityName = "LILS";
		String name = "testVar";
		String value = "test value";
		Response response;

		// First, check whether the confVar is set to this value already - if so, choose
		// a different value
		if (value.equals(confVarRepository.getConfVar(name))) {
			value = "different test value";
		}

		response = adminResource.setConfVar(name, facilityName, adminSessionId, value);
		assertEquals(200, response.getStatus());

		assertEquals(value, confVarRepository.getConfVar(name).getValue());

		// Test behaviour for non-admin user
		try {
			response = adminResource.setConfVar(name, facilityName, nonAdminSessionId, value);
			// We should not see the following
			System.out.println("DEBUG: AdminRT.setConfVar response: " + response.getStatus() + ", "
					+ (String) response.getEntity());
			fail("AdminResource.setConfVar did not raise exception for non-admin user");
		} catch (ForbiddenException fe) {
		}
	}

	private Download findDownload(List<Download> downloads, Long downloadId) {

		for (Download download : downloads) {
			if (download.getId().equals(downloadId))
				return download;
		}
		return null;
	}

}
