package org.icatproject.topcat;

import org.icatproject.topcat.exceptions.InternalException;
import org.icatproject.topcat.repository.CartRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ejb.EJB;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;

@Singleton
public class CartChecker {
    private static final Logger logger = LoggerFactory.getLogger(CartChecker.class);
    private String anonUserName;
    private FacilityMap facilityMap;

    @EJB
    private CartRepository cartRepository;

    /**
     * @throws InternalException if FacilityMap fails to intitalise due to a bad
     *                           properties file
     */
    public CartChecker() throws InternalException {
        this.anonUserName = Properties.getInstance().getProperty("anonUserName", "");
        this.facilityMap = FacilityMap.getInstance();
    }

    /**
     * Constructor for use in testing where specific parameters may be needed.
     * 
     * @param anonUserName   Anonymous username without session id suffix
     * @param facilityMap    Map of facility specific properties
     * @param cartRepository EJB for managing Cart entities
     */
    public CartChecker(String anonUserName, FacilityMap facilityMap, CartRepository cartRepository) {
        this.anonUserName = anonUserName;
        this.facilityMap = facilityMap;
        this.cartRepository = cartRepository;
    }

    /**
     * Poll for the deletion of old carts at midnight.
     */
    @Schedule(hour = "0", minute = "0", second = "0")
    void poll() {
        logger.trace("Beginning deletion of old carts");
        try {
            for (String facilityName : facilityMap.getFacilities()) {
                if (!anonUserName.equals("")) {
                    removeOldCarts(facilityName, facilityMap.getMaxCartAgeDaysAnon(facilityName), true);
                }
                removeOldCarts(facilityName, facilityMap.getMaxCartAgeDaysAuthenticated(facilityName), false);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        logger.trace("Deletion of old carts completed");
    }

    /**
     * Deletes Carts from the specified facilityName that are older than
     * maxCartAgeDays.
     * 
     * @param facilityName   Cart facilityName to query for
     * @param maxCartAgeDays Only Carts where updatedAt is older than this will be
     *                       deleted
     * @param isAnon         Whether to delete carts that do (or do not) match
     *                       anonUserName
     */
    public void removeOldCarts(String facilityName, Integer maxCartAgeDays, boolean isAnon) {
        if (maxCartAgeDays != null) {
            int numberDeleted = cartRepository.deleteCarts(facilityName, maxCartAgeDays, anonUserName, isAnon);
            String format = "Deleted {} Carts over {} days old for {} users at {}";
            logger.info(format, numberDeleted, maxCartAgeDays, isAnon ? "anon" : "authenticated", facilityName);
        } else {
            String format = "Deletion of carts disabled for {} users at {}";
            logger.trace(format, isAnon ? "anon" : "authenticated", facilityName);
        }
    }
}
