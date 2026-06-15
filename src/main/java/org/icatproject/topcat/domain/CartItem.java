package org.icatproject.topcat.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.icatproject.topcat.icatEntities.IcatEntity;

@Entity
@Table(name = "CARTITEM")
@XmlRootElement
public class CartItem implements Serializable {
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

    @Column(name = "NAME", nullable = true)
    private String name;

    @Column(name = "FILE_COUNT", nullable=false)
    private long fileCount;

    @Column(name = "FILE_SIZE", nullable=false)
    private long fileSize;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "cartItem", orphanRemoval = true)
    private List<ParentEntity> parentEntities = new ArrayList<ParentEntity>();

    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.PERSIST)
    @JoinColumn(name= "CART_ID")
    private Cart cart;

    public CartItem() {
    }

    /**
     * Construct a new CartItem for an ICAT entity and existing cart.
     * 
     * @param entity Representation of an ICAT Investigation, Dataset or Datafile with
     *               common fields defined
     * @param cart   Cart entity to which this CartItem belongs
     */
    public CartItem(IcatEntity entity, Cart cart) {
        this.entityType = EntityType.valueOf(entity.getClass().getSimpleName().toLowerCase());
        this.entityId = entity.getId();
        this.name = entity.getName();
        this.fileCount = entity.getFileCount();
        this.fileSize = entity.getFileSize();
        this.cart = cart;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getFileCount() {
        return fileCount;
    }

    public void setFileCount(long count) {
        this.fileCount = count;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long size) {
        this.fileSize = size;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public List<ParentEntity> getParentEntities() {
        return parentEntities;
    }

    public void setParentEntities(List<ParentEntity> parentEntities) {
        this.parentEntities = parentEntities;
    }

    @JsonbTransient
    @XmlTransient
    public Cart getCart() {
        return cart;
    }

    public void setCart(Cart cart) {
        this.cart = cart;
    }

    public void addParent(ParentEntity parentEntity) {
        parentEntities.add(parentEntity);
    }

    public String toString() {
        return "entityType: " + entityType + " entityId: " + entityId + " fileCount: " + fileCount + " fileSize: " + fileSize;
    }
}
