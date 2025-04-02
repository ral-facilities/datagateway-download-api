package org.icatproject.topcat;

import java.util.*;
import java.lang.reflect.*;

import static org.junit.Assert.*;
import org.junit.*;

import jakarta.json.*;
import jakarta.ejb.EJB;

import org.icatproject.topcat.httpclient.HttpClient;
import org.icatproject.topcat.httpclient.Response;
import org.icatproject.topcat.domain.*;
import org.icatproject.topcat.exceptions.TopcatException;

import java.net.URLEncoder;

import org.icatproject.topcat.repository.CacheRepository;

import java.sql.*;

public class IcatClientTest {

	@EJB
	private CacheRepository cacheRepository;

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
	public void testGetUserName() throws Exception {
		IcatClient icatClient = new IcatClient("https://localhost:8181", sessionId);
		assertEquals("simple/root", icatClient.getUserName());
	}

	@Test
	public void testIsAdmin() throws Exception {
		IcatClient icatClient = new IcatClientUserIsAdmin("https://localhost:8181", sessionId);
		assertTrue(icatClient.isAdmin());
		icatClient = new IcatClientUserIsAdmin("https://localhost:8181", "bogus-session-id");
		assertFalse(icatClient.isAdmin());

		icatClient = new IcatClientUserNotAdmin("https://localhost:8181", sessionId);
		assertFalse(icatClient.isAdmin());
		icatClient = new IcatClientUserNotAdmin("https://localhost:8181", "bogus-session-id");
		assertFalse(icatClient.isAdmin());
	}

	@Test
	public void testGetEntities() throws Exception {
		IcatClient icatClient = new IcatClientUserIsAdmin("https://localhost:8181", sessionId);

		List<Long> ids = new ArrayList<Long>();

		List<JsonObject> results = icatClient.getEntities("investigation", ids);
		assertEquals(0, results.size());
		results = icatClient.getEntities("dataset", ids);
		assertEquals(0, results.size());
		results = icatClient.getEntities("datafile", ids);
		assertEquals(0, results.size());

		List<Long> investigationIds = new ArrayList<Long>();
		ResultSet investigations = connection.createStatement().executeQuery("SELECT * from INVESTIGATION limit 0, 1");
		investigations.next();
		investigationIds.add(investigations.getLong("ID"));

		results = icatClient.getEntities("investigation", investigationIds);
		assertEquals(1, results.size());

		List<Long> datasetIds = new ArrayList<Long>();
		ResultSet datasets = connection.createStatement().executeQuery("SELECT * from DATASET limit 0, 1");
		datasets.next();
		datasetIds.add(datasets.getLong("ID"));

		results = icatClient.getEntities("dataset", datasetIds);
		assertEquals(1, results.size());
		assertNotNull(results.get(0).getJsonObject("investigation"));

		List<Long> datafileIds = new ArrayList<Long>();
		ResultSet datafiles = connection.createStatement().executeQuery("SELECT * from DATAFILE");
		datafiles.next();
		datafileIds.add(datafiles.getLong("ID"));

		results = icatClient.getEntities("datafile", datafileIds);
		assertEquals(1, results.size());
		assertNotNull(results.get(0).getJsonObject("dataset"));
		assertNotNull(results.get(0).getJsonObject("dataset").getJsonObject("investigation"));

		for (long i = 2; i <= 10001; i++) {
			datafiles.next();
			datafileIds.add(datafiles.getLong("ID"));
		}

		results = icatClient.getEntities("datafile", datafileIds);
		assertEquals(10001, results.size());

	}

	@Test
	public void testGetFullName() throws Exception {
		IcatClient icatClient = new IcatClient("https://localhost:8181", sessionId);
		String fullName = icatClient.getFullName();

		assertNotNull(fullName);
		// assertTrue(fullName.length() > 0);
	}

	@Test
	public void testCheckUserNotFound() throws Exception {
		IcatClient icatClient = new IcatClient("https://localhost:8181", sessionId);
		String userName = "db/test";
		String i0Condition = "EXISTS ( SELECT o FROM InstrumentScientist o WHERE o.instrument.name='I0' AND o.user=user )";
		String instrumentScientistCondition = "user.instrumentScientists IS NOT EMPTY";
		String principalInvestigatorCondition = "EXISTS ( SELECT o FROM InvestigationUser o WHERE o.role='PRINCIPAL_INVESTIGATOR' AND o.user=user )";
		String investigationUserCondition = "user.investigationUsers IS NOT EMPTY";
		String groupingCondition = "EXISTS ( SELECT o FROM UserGroup o WHERE o.grouping.name='principal_beamline_scientists' AND o.user=user )";
		assertEquals(0, icatClient.checkUser(userName, i0Condition));
		assertEquals(0, icatClient.checkUser(userName, instrumentScientistCondition));
		assertEquals(0, icatClient.checkUser(userName, principalInvestigatorCondition));
		assertEquals(0, icatClient.checkUser(userName, investigationUserCondition));
		assertEquals(0, icatClient.checkUser(userName, groupingCondition));
	}

	@Test
	public void testCheckUserFound() throws Exception {
		IcatClient icatClient = new IcatClient("https://localhost:8181", sessionId);
		JsonObject user = icatClient.getEntity("User");
		JsonObject instrument = icatClient.getEntity("Instrument");
		JsonObject investigation = icatClient.getEntity("Investigation");
		String userName = user.getString("name");
		long userId = user.getJsonNumber("id").longValueExact();
		long instrumentId = instrument.getJsonNumber("id").longValueExact();
		long investigationId = investigation.getJsonNumber("id").longValueExact();
		
		HttpClient httpClient = new HttpClient("https://localhost:8181/icat");

		JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
		JsonObjectBuilder instrumentScientistBuilder = Json.createObjectBuilder();
		JsonObjectBuilder instrumentScientistInnerBuilder = Json.createObjectBuilder();
		JsonObjectBuilder investigationUserBuilder = Json.createObjectBuilder();
		JsonObjectBuilder investigationUserInnerBuilder = Json.createObjectBuilder();
		JsonObjectBuilder groupingInnerBuilder = Json.createObjectBuilder();
		JsonObjectBuilder userGroupInnerBuilder = Json.createObjectBuilder();
		JsonObjectBuilder userBuilder = Json.createObjectBuilder();
		JsonObjectBuilder instrumentBuilder = Json.createObjectBuilder();
		JsonObjectBuilder investigationBuilder = Json.createObjectBuilder();
		JsonObjectBuilder groupingBuilder = Json.createObjectBuilder();
		JsonObjectBuilder userGroupBuilder = Json.createObjectBuilder();

		// Need to create a Grouping first, then a UserGrouping second
		groupingInnerBuilder.add("name", "principal_beamline_scientists");
		groupingBuilder.add("Grouping", groupingInnerBuilder);
		arrayBuilder.add(groupingBuilder);

		String data = "sessionId=" + sessionId + "&entities=" + arrayBuilder.build();
		Response response = httpClient.post("entityManager", new HashMap<>(), data);
		JsonArray responseArray = Utils.parseJsonArray(response.toString());
		long groupingId = responseArray.getJsonNumber(0).longValueExact();
		arrayBuilder = Json.createArrayBuilder();
		groupingBuilder = Json.createObjectBuilder();
		
		userBuilder.add("id", userId);
		instrumentBuilder.add("id", instrumentId);
		investigationBuilder.add("id", investigationId);
		groupingBuilder.add("id", groupingId);
		JsonObject userObject = userBuilder.build();

		instrumentScientistInnerBuilder.add("user", userObject);
		instrumentScientistInnerBuilder.add("instrument", instrumentBuilder);
		instrumentScientistBuilder.add("InstrumentScientist", instrumentScientistInnerBuilder);
		arrayBuilder.add(instrumentScientistBuilder);

		investigationUserInnerBuilder.add("user", userObject);
		investigationUserInnerBuilder.add("investigation", investigationBuilder);
		investigationUserInnerBuilder.add("role", "PRINCIPAL_INVESTIGATOR");
		investigationUserBuilder.add("InvestigationUser", investigationUserInnerBuilder);
		arrayBuilder.add(investigationUserBuilder);

		userGroupInnerBuilder.add("user", userObject);
		userGroupInnerBuilder.add("grouping", groupingBuilder);
		userGroupBuilder.add("UserGroup", userGroupInnerBuilder);
		arrayBuilder.add(userGroupBuilder);

		data = "sessionId=" + sessionId + "&entities=" + arrayBuilder.build();
		response = httpClient.post("entityManager", new HashMap<>(), data);
		responseArray = Utils.parseJsonArray(response.toString());
		long instrumentScientistId = responseArray.getJsonNumber(0).longValueExact();
		long investigationUserId = responseArray.getJsonNumber(1).longValueExact();
		try {
			String i0Condition = "EXISTS ( SELECT o FROM InstrumentScientist o WHERE o.instrument.name='I0' AND o.user=user )";
			String instrumentScientistCondition = "user.instrumentScientists IS NOT EMPTY";
			String principalInvestigatorCondition = "EXISTS ( SELECT o FROM InvestigationUser o WHERE o.role='PRINCIPAL_INVESTIGATOR' AND o.user=user )";
			String investigationUserCondition = "user.investigationUsers IS NOT EMPTY";
			String groupingCondition = "EXISTS ( SELECT o FROM UserGroup o WHERE o.grouping.name='principal_beamline_scientists' AND o.user=user )";
			assertEquals(1, icatClient.checkUser(userName, i0Condition));
			assertEquals(1, icatClient.checkUser(userName, instrumentScientistCondition));
			assertEquals(1, icatClient.checkUser(userName, principalInvestigatorCondition));
			assertEquals(1, icatClient.checkUser(userName, investigationUserCondition));
			assertEquals(1, icatClient.checkUser(userName, groupingCondition));
		} finally {
			arrayBuilder = Json.createArrayBuilder();
			instrumentScientistBuilder = Json.createObjectBuilder();
			investigationUserBuilder = Json.createObjectBuilder();
			groupingBuilder = Json.createObjectBuilder();
			instrumentScientistInnerBuilder = Json.createObjectBuilder();
			investigationUserInnerBuilder = Json.createObjectBuilder();
			groupingInnerBuilder = Json.createObjectBuilder();

			instrumentScientistInnerBuilder.add("id", instrumentScientistId);
			instrumentScientistBuilder.add("InstrumentScientist", instrumentScientistInnerBuilder);
			arrayBuilder.add(instrumentScientistBuilder);

			investigationUserInnerBuilder.add("id", investigationUserId);
			investigationUserBuilder.add("InvestigationUser", investigationUserInnerBuilder);
			arrayBuilder.add(investigationUserBuilder);

			groupingInnerBuilder.add("id", groupingId);
			groupingBuilder.add("Grouping", groupingInnerBuilder);
			arrayBuilder.add(groupingBuilder);

			httpClient.delete("entityManager?sessionId=" + sessionId + "&entities=" + arrayBuilder.build(), new HashMap<>());
		}
	}

	@Test
	public void testGetDatasetFileCount() throws TopcatException {
		IcatClient icatClient = new IcatClient("https://localhost:8181", sessionId);
		long datasetId = icatClient.getEntity("Dataset").getJsonNumber("id").longValueExact();
		assertNotEquals(0, icatClient.getDatasetFileCount(datasetId));
	}

	@Test
	public void testGetDatasetFileSize() throws TopcatException {
		IcatClient icatClient = new IcatClient("https://localhost:8181", sessionId);
		long datasetId = icatClient.getEntity("Dataset").getJsonNumber("id").longValueExact();
		assertNotEquals(0, icatClient.getDatasetFileSize(datasetId));
	}

	/*
	 * @Test public void testGetSize() throws Exception { IcatClient icatClient =
	 * new IcatClient("https://localhost:8181", sessionId);
	 * 
	 * List<Long> emptyIds = new ArrayList<Long>();
	 * 
	 * assertEquals((long) 0, (long) icatClient.getSize(cacheRepository, emptyIds,
	 * emptyIds, emptyIds));
	 * 
	 * List<Long> ids = new ArrayList<Long>(); ids.add((long) 1); ids.add((long) 2);
	 * ids.add((long) 3);
	 * 
	 * assertTrue(icatClient.getSize(cacheRepository, ids, emptyIds, emptyIds) >
	 * (long) 0); assertTrue(icatClient.getSize(cacheRepository, emptyIds, ids,
	 * emptyIds) > (long) 0); assertTrue(icatClient.getSize(cacheRepository,
	 * emptyIds, emptyIds, ids) > (long) 0);
	 * assertTrue(icatClient.getSize(cacheRepository, ids, ids, ids) > (long) 0); }
	 */

	private Map<String, List<Long>> createEmptyEntityTypeEntityIds() {
		Map<String, List<Long>> out = new HashMap<String, List<Long>>();
		out.put("investigation", new ArrayList<Long>());
		out.put("dataset", new ArrayList<Long>());
		out.put("datafile", new ArrayList<Long>());
		return out;
	}

	class IcatClientUserIsAdmin extends IcatClient {

		public IcatClientUserIsAdmin(String url, String sessionId) {
			super(url, sessionId);
		}

		protected String[] getAdminUserNames() throws Exception {
			return new String[] { "simple/root" };
		}
	}

	class IcatClientUserNotAdmin extends IcatClient {

		public IcatClientUserNotAdmin(String url, String sessionId) {
			super(url, sessionId);
		}

		protected String[] getAdminUserNames() throws Exception {
			return new String[] { "db/test" };
		}
	}

}