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

import org.icatproject.topcat.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Stateless
@LocalBean
public class CartRepository {
    private static final Logger logger = LoggerFactory.getLogger(CartRepository.class);

    @PersistenceContext(unitName="topcat")
    EntityManager em;

    /**
     * @param cart Cart to be persisted and flushed
     */
    public void createCart(Cart cart) {
        em.persist(cart);
        em.flush();
    }

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
     * If a Cart exists with the specified parameters, remove it.
     * 
     * @param facilityName ICAT Facility.name to which the cart belongs
     * @param userName     ICAT User.name to whom the cart belongs
     */
    public void removeCart(String facilityName, String userName) {
        Cart  cart = getCart(userName, facilityName);
        if (cart != null) {
            em.remove(cart);
            em.flush();
        }
    }
}
