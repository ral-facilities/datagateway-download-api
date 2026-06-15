package org.icatproject.topcat.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.UniqueConstraint;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.eclipse.persistence.annotations.CascadeOnDelete;
import org.icatproject.topcat.FacilityMap;
import org.icatproject.topcat.exceptions.BadRequestException;
import org.icatproject.topcat.exceptions.TopcatException;

@Entity
@Table(
        name = "CART",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {
                        "FACILITY_NAME",
                        "USER_NAME"
                }
        )
)
@CascadeOnDelete
@XmlRootElement
public class Cart implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "FACILITY_NAME", nullable = false)
    private String facilityName;

    @Column(name = "USER_NAME", nullable = false)
    private String userName;

    @Column(name = "FILE_COUNT", nullable=false)
    private long fileCount;

    @Column(name = "FILE_SIZE", nullable=false)
    private long fileSize;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "cart", orphanRemoval = true)
    private List<CartItem> cartItems = new ArrayList<CartItem>();

    @Column(name = "CREATED_AT", nullable=false, updatable=false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Column(name = "UPDATED_AT", nullable=false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;


    public Cart() {

    }

    /**
     * Construct a new Cart with the specified parameters.
     * 
     * @param facilityName ICAT Facility.name to which the cart belongs
     * @param userName     ICAT User.name to whom the cart belongs
     */
    public Cart(String facilityName, String userName) {
        this.facilityName = facilityName;
        this.userName = userName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
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

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PreUpdate
    private void updateAt() {
        this.updatedAt = new Date();
    }

    @PrePersist
    private void createAt() {
        this.createdAt = this.updatedAt = new Date();
    }

    public List<CartItem> getCartItems() {
        return cartItems;
    }

    public void setCartItems(List<CartItem> cartItems) {
        this.cartItems = cartItems;
    }

    /**
     * @param increment amount to increase the cart's fileCount by
     * @throws TopcatException if countLimit set and exceeded by incrementing
     */
    public void incrementFileCount(long increment) throws TopcatException {
        fileCount += increment;
        Long countLimit = FacilityMap.getInstance().getCountLimit(facilityName);
        if (countLimit != null && fileCount > countLimit) {
            throw new BadRequestException("Unable to add items to cart: number of files exceeds limit");
        }
    }

    /**
     * @param increment amount to increase the cart's fileSize by
     * @throws TopcatException if sizeLimit set and exceeded by incrementing
     */
    public void incrementFileSize(long increment) throws TopcatException {
        fileSize += increment;
        Long sizeLimit = FacilityMap.getInstance().getSizeLimit(facilityName);
        if (sizeLimit != null && fileSize > sizeLimit) {
            throw new BadRequestException("Unable to add items to cart: size of files exceeds limit");
        }
    }

    /**
     * Decrement fileCount/fileSize by that of cartItem.
     * 
     * @param cartItem CartItem being removed from the Cart
     */
    public void decrementFileCountSize(CartItem cartItem) {
        fileCount -= cartItem.getFileCount();
        fileSize -= cartItem.getFileSize();
    }

    /**
     * Add cartItem to the cart and increment fileCount/fileSize.
     * 
     * @param cartItem CartItem being added to the Cart
     * @throws TopcatException if countLimit/sizeLimit are set and exceeded
     */
    public void addCartItem(CartItem cartItem) throws TopcatException {
        cartItems.add(cartItem);
        incrementFileCount(cartItem.getFileCount());
        incrementFileSize(cartItem.getFileSize());
    }

    /**
     * Removes a Collection of CartItems without changing fileCount/fileSize.
     * 
     * @param cartItems Collection of CartItems to be removed from the cart
     */
    public void removeCartItems(Collection<CartItem> cartItems) {
        this.cartItems.removeAll(cartItems);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("id: " + id);
        sb.append(" ");
        sb.append("facilityName:" + facilityName);
        sb.append(" ");
        sb.append("userName:" + userName);
        sb.append(" ");
        sb.append("CartItems:" + this.getCartItems().size());

        return sb.toString();
    }

}
