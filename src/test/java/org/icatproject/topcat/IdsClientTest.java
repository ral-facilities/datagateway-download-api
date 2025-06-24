package org.icatproject.topcat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class IdsClientTest {


	@Test
	public void testChunkOffsets() throws Exception {
		IdsClient idsClient = new IdsClient("https://localhost:8181");
		Method chunkOffsets = idsClient.getClass().getDeclaredMethod("chunkOffsets", String.class, List.class, List.class, List.class);
		chunkOffsets.setAccessible(true);

		for(int i = 0; i < 10; i++){
			// Use the same IDs for each list of investigation/dataset/datafileIds
			List<Long> investigationIds = generateIds(i * 100, 1000);
			List<Long> datasetIds = generateIds(i * 100, 1000);
			List<Long> datafileIds = generateIds(i * 100, 1000);
			List<String> chunks = (List<String>) chunkOffsets.invoke(idsClient, "preparedData?sessionIds=312313-21312-312&", investigationIds, datasetIds, datafileIds);
			for(String chunk : chunks){
				assertTrue(chunk.length() <= 2048);
			}
			// Each ID ought to appear *somewhere* as an investigation/dataset/datafileId
			// Careful: build process may have emptied the original entityIds lists
			List<Long> entityIds = generateIds(i * 100, 1000);
			boolean allFound = true;
			boolean foundRepeats = false;
			for( Long id : entityIds ) {
				boolean foundInvestigation = false;
				boolean foundDataset = false;
				boolean foundDatafile = false;
				for( String chunk : chunks ) {
					// Only look if we haven't found it in a previous chunk
					// Also look for duplicates
					if( ! foundInvestigation ) {
						foundInvestigation = chunkContains(chunk, "investigation",id);
					} else {
						foundRepeats = chunkContains(chunk, "investigation",id);
					}
					if( ! foundDataset ) {
						foundDataset       = chunkContains(chunk, "dataset",id);
					} else {
						foundRepeats = chunkContains(chunk, "investigation",id);
					}
					if( ! foundDatafile ) {
						foundDatafile      = chunkContains(chunk, "datafile",id);
					} else {
						foundRepeats = chunkContains(chunk, "investigation",id);
					}
				}
				allFound = allFound && foundInvestigation && foundDataset && foundDatafile;
			}
			assertTrue(allFound, "Not all IDs found in chunks");
			assertFalse(foundRepeats, "At least one ID was repeated");
		}

		String expected = "test?investigationIds=1,2,3&datasetIds=4,5,6&datafileIds=7,8,9";
		List<String> offsets = (List<String>) chunkOffsets.invoke(idsClient, "test?", generateIds(1, 3), generateIds(4, 3), generateIds(7, 3));
		String actual = offsets.get(0);

		assertEquals(expected, actual);
	}
	
	@Test
	public void testParseTimeout() throws Exception {
		IdsClient idsClient = new IdsClient("https://localhost:8181");
		Method parseTimeout = idsClient.getClass().getDeclaredMethod("parseTimeout", String.class);
		parseTimeout.setAccessible(true);
		
		assertEquals(parseTimeout.invoke(idsClient, "1000"), 1000);
		assertEquals(parseTimeout.invoke(idsClient, "10s"), 10000);
		assertEquals(parseTimeout.invoke(idsClient, "10m"), 600000);
		assertEquals(parseTimeout.invoke(idsClient, "-1"), -1);
		assertEquals(parseTimeout.invoke(idsClient, "rubbish"), -1);
	}

	private List<Long> generateIds(int offset, int count){
		List<Long> out = new ArrayList<Long>();

		for(int i = 0; i < count; i++){
			out.add(Long.valueOf(offset + i));
		}

		return out;
	}
	
	private boolean chunkContains(String chunk, String entityType, Long id) {
		// return true if chunk contains a substring of the form "<entity>Ids=m1,m2,...,mN" with some mi == id
		return chunk.matches(".*" + entityType + "Ids=[\\d+,]*" + id.toString() + "[,\\d+]*.*");
	}

}
