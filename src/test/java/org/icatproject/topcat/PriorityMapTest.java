package org.icatproject.topcat;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

import org.icatproject.topcat.exceptions.InternalException;
import org.junit.Test;

public class PriorityMapTest {
    @Test
    public void testPriorityMap() throws InternalException {
        PriorityMap priorityMap = new PriorityMap();
        assertEquals(2, priorityMap.getDefaultPriority());
        assertEquals(2, priorityMap.getAuthenticatedPriority());
        assertEquals(0, priorityMap.getMapping().size());
    }

    @Test
    public void testSetAuthenticatedPriority() throws InternalException, NoSuchMethodException, SecurityException,
            IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        PriorityMap priorityMap = new PriorityMap();
        Method method = PriorityMap.class.getDeclaredMethod("setAuthenticatedPriority", String.class);
        method.setAccessible(true);

        // Even though we tried to disable authenticated queuing, default priority is 2
        // so we'll use that
        method.invoke(priorityMap, "0");
        assertEquals(2, priorityMap.getDefaultPriority());
        assertEquals(2, priorityMap.getAuthenticatedPriority());

        // Even though we tried set low priority for authenticated users, default
        // priority is 1 so we'll use that
        method.invoke(priorityMap, "3");
        assertEquals(2, priorityMap.getDefaultPriority());
        assertEquals(2, priorityMap.getAuthenticatedPriority());

        // Should be able to set a lower, positive int
        method.invoke(priorityMap, "1");
        assertEquals(2, priorityMap.getDefaultPriority());
        assertEquals(1, priorityMap.getAuthenticatedPriority());
    }

    @Test
    public void testParseObject() throws InternalException, NoSuchMethodException, SecurityException,
            IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        PriorityMap priorityMap = new PriorityMap();
        Method method = PriorityMap.class.getDeclaredMethod("parseObject", String.class, String.class);
        method.setAccessible(true);

        // Only values which are <2 (the default) and >0 (disabled) should be allowed
        String objectString = "{\"ABC\": 1, \"DEF\": 2, \"GHI\": 3, \"JKL\": 1, \"MNO\": 0}";
        String conditionPrefix = "EXISTS ( SELECT o FROM InstrumentScientist o WHERE o.instrument.name='";
        String expected = conditionPrefix + "ABC' AND o.user=user ) OR "+ conditionPrefix + "JKL' AND o.user=user )";
        method.invoke(priorityMap, objectString, conditionPrefix);
        HashMap<Integer, String> mapping = priorityMap.getMapping();
        assertEquals(1, mapping.size());
        assertEquals(expected, mapping.get(1));
    }
}
