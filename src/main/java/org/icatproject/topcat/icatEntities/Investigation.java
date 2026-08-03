package org.icatproject.topcat.icatEntities;

import jakarta.json.JsonObject;

/**
 * Minimal representation of an ICAT Investigation, to be used when building
 * CartItems.
 */
public class Investigation extends IcatEntity {
    /**
     * @param source JsonObject representation of an ICAT Investigation to construct
     *               from
     */
    public Investigation(JsonObject source) {
        super(source);
        fileCount = source.getJsonNumber("fileCount").longValueExact();
    }
}
