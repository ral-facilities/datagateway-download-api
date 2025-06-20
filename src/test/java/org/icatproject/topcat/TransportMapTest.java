package org.icatproject.topcat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import org.icatproject.topcat.httpclient.HttpClient;
import org.icatproject.topcat.httpclient.Response;
import org.junit.Test;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

public class TransportMapTest {
    @Test
    public void testTransportMap() throws Exception {
        System.out.println("DEBUG: testTransportMap");
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        JsonObjectBuilder userInnerBuilder = Json.createObjectBuilder();
        JsonObjectBuilder groupingInnerBuilder = Json.createObjectBuilder();
        JsonObjectBuilder userGroupInnerBuilder = Json.createObjectBuilder();
        JsonObjectBuilder userBuilder = Json.createObjectBuilder();
        JsonObjectBuilder groupingBuilder = Json.createObjectBuilder();
        JsonObjectBuilder userGroupBuilder = Json.createObjectBuilder();
   		HttpClient httpClient = new HttpClient("https://localhost:8181/icat");
        IcatClient icatClient = new IcatClient("https://localhost:8181");
        String stringResponse = icatClient.login("simple", "root", "pw");
        String sessionId = Utils.parseJsonObject(stringResponse).getString("sessionId");
        icatClient.setSessionId(sessionId);
        TransportMap transportMap = new TransportMap();

        // No explicit mapping should return true, for backwards compatibility
        assertTrue(transportMap.isAllowed("", "", "root", icatClient));
        assertTrue(transportMap.isAllowed("LILS", "", "root", icatClient));

        // lils mechanism only allowed for specific groupings
        assertFalse(transportMap.isAllowed("LILS", "lils", "root", icatClient));
        userInnerBuilder.add("name", "simple/root");
        userBuilder.add("User", userInnerBuilder);
        arrayBuilder.add(userBuilder);

        groupingInnerBuilder.add("name", "principal_beamline_scientists");
        groupingBuilder.add("Grouping", groupingInnerBuilder);
        arrayBuilder.add(groupingBuilder);

        String data = "sessionId=" + sessionId + "&entities=" + arrayBuilder.build();
        Response response = httpClient.post("entityManager", new HashMap<>(), data);
        System.out.println(response);
        JsonArray responseArray = Utils.parseJsonArray(response.toString());
        long userId = responseArray.getJsonNumber(0).longValueExact();
        long groupingId = responseArray.getJsonNumber(1).longValueExact();
        arrayBuilder = Json.createArrayBuilder();
        userBuilder = Json.createObjectBuilder();
        groupingBuilder = Json.createObjectBuilder();
    
        userBuilder.add("id", userId);
        groupingBuilder.add("id", groupingId);
        JsonObject userObject = userBuilder.build();

        userGroupInnerBuilder.add("user", userObject);
        userGroupInnerBuilder.add("grouping", groupingBuilder);
        userGroupBuilder.add("UserGroup", userGroupInnerBuilder);
        arrayBuilder.add(userGroupBuilder);

        data = "sessionId=" + sessionId + "&entities=" + arrayBuilder.build();
        response = httpClient.post("entityManager", new HashMap<>(), data);
        try {
            assertTrue(transportMap.isAllowed("LILS", "lils", "root", icatClient));
        } finally {
			arrayBuilder = Json.createArrayBuilder();
			userBuilder = Json.createObjectBuilder();
			userInnerBuilder = Json.createObjectBuilder();
			groupingBuilder = Json.createObjectBuilder();
			groupingInnerBuilder = Json.createObjectBuilder();

			userInnerBuilder.add("id", userId);
			userBuilder.add("User", userInnerBuilder);
			arrayBuilder.add(userBuilder);

			groupingInnerBuilder.add("id", groupingId);
			groupingBuilder.add("Grouping", groupingInnerBuilder);
			arrayBuilder.add(groupingBuilder);

			response = httpClient.delete("entityManager?sessionId=" + sessionId + "&entities=" + arrayBuilder.build(), new HashMap<>());
            System.out.println(response);
        }

        // globus mechanism not allowed for specific prefixes
        assertTrue(transportMap.isAllowed("LILS", "globus", "root", icatClient));
        assertFalse(transportMap.isAllowed("LILS", "globus", "db/root", icatClient));

        // Mechanism defined, but without allow
        assertTrue(transportMap.isAllowed("LILS", "http", "root", icatClient));
    }
}
