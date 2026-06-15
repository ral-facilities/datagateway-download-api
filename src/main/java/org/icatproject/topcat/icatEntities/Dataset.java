package org.icatproject.topcat.icatEntities;

import jakarta.json.JsonObject;

/**
 * Minimal representation of an ICAT Dataset, to be used when building
 * CartItems.
 */
public class Dataset extends IcatEntity {
    private long investigationId;

    /**
     * @param source JsonObject representation of an ICAT Dataset to construct from
     */
    public Dataset(JsonObject source) {
        super(source);
        fileCount = source.getJsonNumber("fileCount").longValueExact();
        investigationId = source.getJsonObject("investigation").getJsonNumber("id").longValueExact();
    }

    public long getInvestigationId() {
        return investigationId;
    }
}
