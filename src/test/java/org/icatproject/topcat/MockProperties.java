package org.icatproject.topcat;

import java.util.HashMap;
import java.util.Map;

public class MockProperties extends Properties {
		
    private Map<String,String> props;
    
    public MockProperties() {
        props = new HashMap<String,String>();
    }
    
    public void setMockProperty( String propName, String value ) {
        props.put(propName, value);
    }
    
    public String getProperty( String propertyName ) {
        return props.get(propertyName);
    }
    
    public String getProperty( String propertyName, String defaultValue ) {
        String value = props.get(propertyName);
        if( value == null ) {
            value = defaultValue;
        }
        return value;
    }
}
