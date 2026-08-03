package org.icatproject.topcat.icatEntities;

import jakarta.json.JsonObject;

/**
 * Abstract representation of fields common to ICAT Investigations, Datasets and
 * Datafiles, to be used when building CartItems.
 */
public abstract class IcatEntity {
    protected long id;
    protected String name;
    protected long fileCount;
    protected long fileSize;

    /**
     * @param source JsonObject representation of an ICAT entity to construct from
     */
    public IcatEntity(JsonObject source) {
        id = source.getJsonNumber("id").longValueExact();
        name = source.getString("name");
        fileSize = source.getJsonNumber("fileSize").longValueExact();
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getFileCount() {
        return fileCount;
    }

    public long getFileSize() {
        return fileSize;
    }
}
