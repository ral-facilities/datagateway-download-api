package org.icatproject.topcat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

import org.icatproject.topcat.exceptions.ForbiddenException;
import org.icatproject.topcat.exceptions.InternalException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public class PriorityMapTest {
    @Test
    public void testPriorityMap() throws InternalException {
        PriorityMap priorityMap = new PriorityMap();
        PriorityMap.EntityType[] entityTypes = { PriorityMap.EntityType.visit, PriorityMap.EntityType.files,
                PriorityMap.EntityType.dataCollection };

        for (PriorityMap.EntityType entityType : entityTypes) {
            assertEquals(2, priorityMap.getDefaultPriority(entityType));
            assertEquals(-1, priorityMap.getAuthenticatedPriority(entityType, "anon/anon").intValue());
            assertEquals(2, priorityMap.getAuthenticatedPriority(entityType, "simple/test").intValue());
            assertEquals(2, priorityMap.getAuthenticatedPriority(entityType, "test").intValue());
            HashMap<Integer, String> queryMapping = priorityMap.getQueryMapping(entityType);
            assertEquals(0, queryMapping.size(), queryMapping.toString());
            long userPriority = priorityMap.getUserPriority(entityType, "simple/test");
            assertEquals(1L, userPriority);
            assertEquals(null, priorityMap.getUserPriority(entityType, "simple/fail"));
        }
        assertEquals(0, priorityMap.getDefaultPriority(PriorityMap.EntityType.cart));
    }

    @Test
    public void testParseObject() throws InternalException, NoSuchMethodException, SecurityException,
            IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        PriorityMap priorityMap = new PriorityMap();
        // Only values which are <2 (the default) and >0 (disabled) should be allowed
        String objectString = "{\"ABC\": 1, \"DEF\": 2, \"GHI\": 3, \"JKL\": 1, \"MNO\": 0}";
        String conditionPrefix = "EXISTS ( SELECT o FROM InstrumentScientist o WHERE o.instrument.name='";
        String expected = conditionPrefix + "ABC' AND o.user=user ) OR "+ conditionPrefix + "JKL' AND o.user=user )";
        priorityMap.entityPriorities.get(PriorityMap.EntityType.visit).parseObject(objectString, conditionPrefix);
        HashMap<Integer, String> queryMapping = priorityMap.getQueryMapping(PriorityMap.EntityType.visit);
        assertEquals(1, queryMapping.size(), queryMapping.toString());
        assertEquals(expected, queryMapping.get(1));
    }

    @Test
    public void testCheckAnonDownloadEnabled() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        PriorityMap priorityMap = new PriorityMap();
        Field field = PriorityMap.class.getDeclaredField("anonDownloadEnabled");
        field.setAccessible(true);
        field.setBoolean(priorityMap, false);
        Executable runnable = () -> {priorityMap.checkAnonDownloadEnabled("anon/anon");};
        assertThrows(ForbiddenException.class, runnable);
    }
}
