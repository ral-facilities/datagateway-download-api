package org.icatproject.topcat.icatEntities;

import jakarta.json.JsonObject;

/**
 * Minimal representation of an ICAT Datafile, to be used when building
 * CartItems.
 */
public class Datafile extends IcatEntity {
    private long datasetId;
    private long investigationId;

    /**
     * @param source JsonObject representation of an ICAT Datafile to construct from
     */
    public Datafile(JsonObject source) {
        super(source);
        this.fileCount = 1L;
        JsonObject dataset = source.getJsonObject("dataset");
        datasetId = dataset.getJsonNumber("id").longValueExact();
        investigationId = dataset.getJsonObject("investigation").getJsonNumber("id").longValueExact();
    }

    public long getDatasetId() {
        return datasetId;
    }

    public long getInvestigationId() {
        return investigationId;
    }
}
