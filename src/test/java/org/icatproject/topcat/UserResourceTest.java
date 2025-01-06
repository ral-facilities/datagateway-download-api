package org.icatproject.topcat;

import java.util.*;
import java.io.File;
import java.lang.reflect.*;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import static org.junit.Assert.*;
import org.junit.*;
import org.junit.runner.RunWith;
import jakarta.inject.Inject;

import jakarta.json.*;
import jakarta.ws.rs.core.Response;
import jakarta.ejb.EJB;

import org.icatproject.topcat.httpclient.HttpClient;
import org.icatproject.topcat.domain.*;
import org.icatproject.topcat.exceptions.ForbiddenException;

import java.net.URLEncoder;

import org.icatproject.topcat.repository.CacheRepository;
import org.icatproject.topcat.repository.CartRepository;
import org.icatproject.topcat.repository.DownloadRepository;
import org.icatproject.topcat.repository.DownloadTypeRepository;
import org.icatproject.topcat.web.rest.UserResource;

import java.sql.*;

@RunWith(Arquillian.class)
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

	private Connection connection;

	@BeforeClass
	public static void beforeAll() {
		TestHelpers.installTrustManager();
	}

	@Before
	public void setup() throws Exception {
		HttpClient httpClient = new HttpClient("https://localhost:8181/icat");
		String data = "json=" + URLEncoder.encode(
				"{\"plugin\":\"simple\", \"credentials\":[{\"username\":\"root\"}, {\"password\":\"pw\"}]}", "UTF8");
		String response = httpClient.post("session", new HashMap<String, String>(), data).toString();
		sessionId = Utils.parseJsonObject(response).getString("sessionId");

		connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/icatdb", "icatdbuser", "icatdbuserpw");
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
		// It appears queryOffset cannot be empty!
		String queryOffset = "1 = 1";
		response = userResource.getDownloads(facilityName, sessionId, queryOffset);
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
		response = userResource.getDownloads(facilityName, sessionId, queryOffset);
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
			assertEquals(null, newDownload.getEmail());
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

		response = userResource.setDownloadStatus(downloadId, facilityName, sessionId, downloadStatus);
		assertEquals(200, response.getStatus());

		// and test that the new status has been set

		response = userResource.getDownloads(facilityName, sessionId, queryOffset);
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

		response = userResource.getDownloads(facilityName, sessionId, queryOffset);
		assertEquals(200, response.getStatus());
		downloads = (List<Download>) response.getEntity();

		newDownload = findDownload(downloads, downloadId);
		assertTrue(newDownload.getIsDeleted());
	}

	@Test
	public void testSetDownloadStatus() throws Exception {
		Download testDownload = new Download();
		String facilityName = "LILS";
		testDownload.setFacilityName(facilityName);
		testDownload.setSessionId(sessionId);
		testDownload.setStatus(DownloadStatus.PAUSED);
		testDownload.setIsDeleted(false);
		testDownload.setUserName("simple/root");
		testDownload.setFileName("testFile.txt");
		testDownload.setTransport("http");
		downloadRepository.save(testDownload);

		assertThrows("Cannot modify status of a queued download", ForbiddenException.class, () -> {
			userResource.setDownloadStatus(testDownload.getId(), facilityName, sessionId, DownloadStatus.RESTORING.toString());
		});

		Response response = userResource.getDownloads(facilityName, sessionId, null);
		assertEquals(200, response.getStatus());
		List<Download> downloads = (List<Download>) response.getEntity();

		Download unmodifiedDownload = findDownload(downloads, testDownload.getId());
		assertEquals(DownloadStatus.PAUSED, unmodifiedDownload.getStatus());
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