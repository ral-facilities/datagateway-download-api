package org.icatproject.topcat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.icatproject.topcat.domain.Cart;
import org.icatproject.topcat.domain.CartItem;
import org.icatproject.topcat.domain.EntityType;
import org.icatproject.topcat.domain.ParentEntity;
import org.icatproject.topcat.exceptions.TopcatException;
import org.icatproject.topcat.icatEntities.Datafile;
import org.icatproject.topcat.icatEntities.Dataset;
import org.icatproject.topcat.icatEntities.IcatEntity;
import org.icatproject.topcat.icatEntities.Investigation;

/**
 * Class for adding CartItems to a Cart whilst tracking those already in the
 * Cart, parenthood, and fileCounts/fileSizes.
 */
public class CartBuilder {

    private final Map<Long, CartItem> investigations = new HashMap<>();
    private final Map<Long, CartItem> datasets = new HashMap<>();
    private final Map<Long, CartItem> datafiles = new HashMap<>();
    private final Map<Long, Set<CartItem>> investigationChildren = new HashMap<>();
    private final Map<Long, Set<CartItem>> datasetChildren = new HashMap<>();
    private Cart cart;
    private IcatClient icatClient;

    /**
     * Initialises Maps of existing CartItems and parent relationships to make
     * further updates more efficient. If this cart predates fileCount/fileSize
     * tracking, and contains CartItems, will query ICAT to set the initial
     * fileCount/fileSize values.
     * 
     * @param cart       Existing, persisted Cart entity
     * @param icatClient ICAT client, with an active session, to use to query for
     *                   fileCounts/fileSizes if not already populated
     * @throws TopcatException if ICAT queries fail, or fileCount/fileSize limits
     *                         are exceeded
     */
    public CartBuilder(Cart cart, IcatClient icatClient) throws TopcatException {
        this.cart = cart;
        this.icatClient = icatClient;
        for (CartItem cartItem : cart.getCartItems()) {
            switch (cartItem.getEntityType()) {
                case investigation:
                    investigations.put(cartItem.getEntityId(), cartItem);
                    break;
                case dataset:
                    datasets.put(cartItem.getEntityId(), cartItem);
                    putChild(cartItem);
                    break;
                case datafile:
                    datafiles.put(cartItem.getEntityId(), cartItem);
                    putChild(cartItem);
                    break;
                default:
            }
        }
        if (cart.getFileSize() == 0L && cart.getFileCount() == 0L) {
            for (Investigation investigation : icatClient.getInvestigations(investigations.keySet())) {
                populateCartItemCountSize(investigations.get(investigation.getId()), investigation);
            }
            for (Dataset dataset : icatClient.getDatasets(datasets.keySet())) {
                populateCartItemCountSize(datasets.get(dataset.getId()), dataset);
            }
            for (Datafile datafile : icatClient.getDatafiles(datafiles.keySet())) {
                populateCartItemCountSize(datafiles.get(datafile.getId()), datafile);
            }
        }
    }

    /**
     * Add all the specified items provided they or their parent(s) are not already
     * in the Cart. If a parent is added, then its children will be removed.
     * Increments fileCount and fileSize for the Cart and throws if limits are
     * defined and exceeded.
     * 
     * @param items a list of entity type (i.e. datafile, dataset or investigation)
     *              and entity id pairs in the form: investigation 2, datafile 1
     * @throws TopcatException if ICAT queries fail, or fileCount/fileSize limits
     *                         are exceeded
     */
    public void addEntities(String items) throws TopcatException {
        Set<Long> investigationIdsToAdd = new HashSet<Long>();
        Set<Long> datasetIdsToAdd = new HashSet<Long>();
        Set<Long> datafileIdsToAdd = new HashSet<Long>();
        for (String item : items.split("\\s*,\\s*")) {
            String[] pair = item.split("\\s+");
            if (pair.length == 2) {
                EntityType entityType = EntityType.valueOf(pair[0]);
                Long entityId = Long.parseLong(pair[1]);
                switch (entityType) {
                    case investigation:
                        if (!investigations.containsKey(entityId)) {
                            investigationIdsToAdd.add(entityId);
                        }
                        break;
                    case dataset:
                        if (!datasets.containsKey(entityId)) {
                            datasetIdsToAdd.add(entityId);
                        }
                        break;
                    case datafile:
                        if (!datafiles.containsKey(entityId)) {
                            datafileIdsToAdd.add(entityId);
                        }
                        break;
                    default:
                }
            }
        }
        for (Investigation investigation : icatClient.getInvestigations(investigationIdsToAdd)) {
            addInvestigation(investigation);
        }
        for (Dataset dataset : icatClient.getDatasets(datasetIdsToAdd)) {
            addDataset(dataset);
        }
        for (Datafile datafile : icatClient.getDatafiles(datafileIdsToAdd)) {
            addDatafile(datafile);
        }
    }

    /**
     * Adds child to investigationChildren and datasetChildren using the parent ICAT
     * entity id as key
     * 
     * @param child CartItem with parents
     */
    private void putChild(CartItem child) {
        for (ParentEntity parentEntity : child.getParentEntities()) {
            switch (parentEntity.getEntityType()) {
                case investigation:
                    putChild(investigationChildren, parentEntity.getEntityId(), child);
                    break;
                case dataset:
                    putChild(datasetChildren, parentEntity.getEntityId(), child);
                    break;
                default:
            }
        }
    }

    /**
     * 
     * @param childrenMap    Map of parent ICAT entity id to a set of all its
     *                       children currently in the cart
     * @param parentEntityId ICAT entity id of the parent of child
     * @param child          CartItem in the cart to be tracked by its parent id
     */
    private static void putChild(Map<Long, Set<CartItem>> childrenMap, Long parentEntityId, CartItem child) {
        childrenMap.computeIfAbsent(parentEntityId, k -> new HashSet<>()).add(child);
    }

    /**
     * Initialises fileCount/fileSize on cartItem and cart for old data before this
     * was tracked.
     * 
     * @param cartItem   CartItem in the cart without fileCount/fileSize populated
     * @param icatEntity Representation of relevant ICAT Investigation, Dataset or
     *                   Datafile fields
     * @throws TopcatException if fileCount/fileSize limits are exceeded
     */
    private void populateCartItemCountSize(CartItem cartItem, IcatEntity icatEntity) throws TopcatException {
        long fileCount = icatEntity.getFileCount();
        long fileSize = icatEntity.getFileSize();
        cartItem.setFileCount(fileCount);
        cartItem.setFileSize(fileSize);
        cart.incrementFileCount(fileCount);
        cart.incrementFileSize(fileSize);
    }

    /**
     * Add investigation if not already in the cart, remove any children and
     * increment fileCount/fileSize.
     * 
     * @param investigation Representation of an ICAT Investigation
     * @throws TopcatException if fileCount/fileSize limits are exceeded
     */
    private void addInvestigation(Investigation investigation) throws TopcatException {
        long id = investigation.getId();
        if (investigations.containsKey(id)) {
            return; // Investigation already present, nothing to do
        }
        Set<CartItem> childrenToRemove = investigationChildren.get(id);
        if (childrenToRemove != null) {
            childrenToRemove.forEach(c -> cart.decrementFileCountSize(c));
            cart.removeCartItems(childrenToRemove);
        }

        CartItem cartItem = new CartItem(investigation, cart);
        cart.addCartItem(cartItem);
        investigations.put(id, cartItem);
    }

    /**
     * Add dataset if it or its parent not already in the cart, remove any children
     * and increment fileCount/fileSize.
     * 
     * @param dataset Representation of an ICAT Dataset
     * @throws TopcatException if fileCount/fileSize limits are exceeded
     */
    private void addDataset(Dataset dataset) throws TopcatException {
        long id = dataset.getId();
        long investigationId = dataset.getInvestigationId();
        if (datasets.containsKey(id) || investigations.containsKey(investigationId)) {
            return; // Dataset or parent already present, nothing to do
        }
        Set<CartItem> childrenToRemove = datasetChildren.get(id);
        if (childrenToRemove != null) {
            childrenToRemove.forEach(c -> cart.decrementFileCountSize(c));
            cart.removeCartItems(childrenToRemove);
        }

        CartItem cartItem = new CartItem(dataset, cart);
        cartItem.addParent(new ParentEntity(EntityType.investigation, investigationId, cartItem));
        cart.addCartItem(cartItem);
        datasets.put(id, cartItem);
        putChild(cartItem);
    }

    /**
     * Add datafile if it or its parent not already in the cart, and increment
     * fileCount/fileSize.
     * 
     * @param investigation Representation of an ICAT Investigation
     * @throws TopcatException if fileCount/fileSize limits are exceeded
     */
    private void addDatafile(Datafile datafile) throws TopcatException {
        long id = datafile.getId();
        long datasetId = datafile.getDatasetId();
        long investigationId = datafile.getInvestigationId();
        if (datafiles.containsKey(id) || datasets.containsKey(datasetId)
                || investigations.containsKey(investigationId)) {
            return; // Datafile or parent already present, nothing to do
        }
        CartItem cartItem = new CartItem(datafile, cart);
        cartItem.addParent(new ParentEntity(EntityType.dataset, datasetId, cartItem));
        cartItem.addParent(new ParentEntity(EntityType.investigation, investigationId, cartItem));
        cart.addCartItem(cartItem);
        datafiles.put(id, cartItem);
        putChild(cartItem);
    }
}
