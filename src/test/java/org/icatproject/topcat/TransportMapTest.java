package org.icatproject.topcat;

import static org.junit.Assert.assertThrows;

import org.icatproject.topcat.exceptions.ForbiddenException;
import org.junit.Test;

public class TransportMapTest {
    @Test
    public void test() throws ForbiddenException {
        TransportMap transportMap = new TransportMap();
        transportMap.checkAllowed("", "", "");
        transportMap.checkAllowed("test", "", "");
        transportMap.checkAllowed("test", "test", "");
        transportMap.checkAllowed("test", "test", "allowed/userName");
        assertThrows(ForbiddenException.class, () -> transportMap.checkAllowed("test", "test", "disallowed/userName"));
    }
}
