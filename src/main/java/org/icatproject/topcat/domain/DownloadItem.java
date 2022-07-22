package org.icatproject.topcat.domain;

import java.io.Serializable;

import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

@Entity
@Table(name = "DOWNLOADITEM")
@NamedQueries({
        @NamedQuery(name = "DownloadItem.findById", query = "SELECT i FROM DownloadItem i WHERE i.id = :id"),
        @NamedQuery(name = "DownloadItem.findByDownloadId", query = "SELECT i FROM DownloadItem i WHERE i.download.id = :id"),
        @NamedQuery(name = "DownloadItem.deleteById", query = "DELETE FROM DownloadItem i WHERE i.id = :id"),
        @NamedQuery(name = "DownloadItem.deleteByDownloadId", query = "DELETE FROM DownloadItem i WHERE i.download.id = :id")
})
@XmlRootElement
public class DownloadItem implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ENTITY_TYPE", nullable = false)
    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    @Column(name = "ENTITY_ID", nullable = false)
    private Long entityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name= "DOWNLOAD_ID")
    private Download download;

    public DownloadItem() {
    }

    public DownloadItem(EntityType entityType, Long entityId) {
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    @JsonbTransient
    @XmlTransient
    public Download getDownload() {
        return download;
    }

    public void setDownload(Download download) {
        this.download = download;
    }

}
