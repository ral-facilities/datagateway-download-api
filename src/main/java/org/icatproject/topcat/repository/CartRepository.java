package org.icatproject.topcat.repository;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Singleton;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.NoResultException;

import org.apache.commons.lang3.time.DateUtils;
import org.icatproject.topcat.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Stateless
@LocalBean
public class CartRepository {
    private static final Logger logger = LoggerFactory.getLogger(CartRepository.class);

    @PersistenceContext(unitName="topcat")
    EntityManager em;

    public Cart getCart(String userName, String facilityName){
        TypedQuery<Cart> query = em.createQuery("select cart from Cart cart where cart.userName = :userName and cart.facilityName = :facilityName", Cart.class)
            .setParameter("userName", userName)
            .setParameter("facilityName", facilityName);
        try {
            return query.getSingleResult();
        } catch(NoResultException e){
            return null;
        }
    }

    /**
     * @param userName     Cart userName
     * @param facilityName Cart facilityName
     * @return new, persisted Cart with the specified parameters
     */
    public Cart createCart(String userName, String facilityName) {
        Cart cart = new Cart();
        cart.setFacilityName(facilityName);
        cart.setUserName(userName);
        em.persist(cart);
        em.flush();
        return cart;
    }

    /**
     * Deletes Carts from the specified facilityName that are older than
     * maxCartAgeDays. Can optionally for just anonymous users, just authenticated
     * users, or both.
     * 
     * @param facilityName   Cart facilityName to query for
     * @param maxCartAgeDays Only Carts where updatedAt is older than this will be
     *                       deleted
     * @param anonUserName   If defined, will include a clause selecting based on
     *                       whether userName matches
     * @param isAnon         Only used if anonUserName is not empty. If true, clause
     *                       on userName is LIKE, if false NOT LIKE.
     * @return number of rows deleted
     */
    public int deleteCarts(String facilityName, int maxCartAgeDays, String anonUserName, boolean isAnon) {
        TypedQuery<Cart> query;
        String qlString = "DELETE FROM Cart cart";
        qlString += " WHERE cart.facilityName = :facilityName AND cart.updatedAt < :updatedAt";
        if (anonUserName.equals("")) {
            query = em.createQuery(qlString, Cart.class);
        } else {
            if (isAnon) {
                query = em.createQuery(qlString + " AND cart.userName LIKE :anonPrefix", Cart.class);
            } else {
                query = em.createQuery(qlString + " AND cart.userName NOT LIKE :anonPrefix", Cart.class);
            }
            query.setParameter("anonPrefix", anonUserName + "/%");
        }
        query.setParameter("facilityName", facilityName);
        query.setParameter("updatedAt", DateUtils.addDays(new Date(), -maxCartAgeDays));
        return query.executeUpdate();
    }

}
