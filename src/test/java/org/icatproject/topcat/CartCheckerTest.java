package org.icatproject.topcat;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.icatproject.topcat.exceptions.InternalException;
import org.icatproject.topcat.repository.CartRepository;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.ejb.EJB;
import jakarta.inject.Inject;

@ArquillianTest
public class CartCheckerTest {
    private static final String anonUserName = "anon/anon/00000000-0000-0000-0000-000000000000";
    private static final String authenticatedUserName = "simple/root";
    private static final String facilityName = "LILS";

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class)
                .addClasses(CartChecker.class, CartRepository.class)
                .addPackages(true, "org.icatproject.topcat.domain", "org.icatproject.topcat.exceptions")
                .addAsResource("META-INF/persistence.xml")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @EJB
    private CartRepository cartRepository;

    @Inject
    private CartChecker cartChecker;

    @BeforeAll
    public static void beforeAll() {
        TestHelpers.installTrustManager();
    }

    @BeforeEach
    public void beforeEach() {
        cartRepository.createCart(anonUserName, facilityName);
        cartRepository.createCart(authenticatedUserName, facilityName);
    }

    @AfterEach
    public void afterEach() {
        cartRepository.deleteCarts(facilityName, 0, "", false);
    }

    @Test
    @Transactional
    public void testAnonUserNameUndefined() throws InternalException {
        MockProperties props = new MockProperties();
        props.setMockProperty("facility.list", "LILS");
        props.setMockProperty("facility.LILS.icatUrl", "https://localhost:8181");
        props.setMockProperty("facility.LILS.idsUrl", "https://localhost:8181");
        props.setMockProperty("facility.LILS.maxCartAgeDays.anon", "0");
        props.setMockProperty("facility.LILS.maxCartAgeDays.authenticated", "0");
        new CartChecker("", new FacilityMap(props), cartRepository).poll();
        assertNull(cartRepository.getCart(anonUserName, facilityName));
        assertNull(cartRepository.getCart(authenticatedUserName, facilityName));
    }

    @Test
    @Transactional
    public void testDeleted() throws InternalException {
        MockProperties props = new MockProperties();
        props.setMockProperty("facility.list", "LILS");
        props.setMockProperty("facility.LILS.icatUrl", "https://localhost:8181");
        props.setMockProperty("facility.LILS.idsUrl", "https://localhost:8181");
        props.setMockProperty("facility.LILS.maxCartAgeDays.anon", "0");
        props.setMockProperty("facility.LILS.maxCartAgeDays.authenticated", "0");
        new CartChecker("anon/anon", new FacilityMap(props), cartRepository).poll();
        assertNull(cartRepository.getCart(anonUserName, facilityName));
        assertNull(cartRepository.getCart(authenticatedUserName, facilityName));
    }

    @Test
    @Transactional
    public void testDisabled() throws InternalException {
        cartChecker.poll();
        assertNotNull(cartRepository.getCart(anonUserName, facilityName));
        assertNotNull(cartRepository.getCart(authenticatedUserName, facilityName));
    }

    @Test
    @Transactional
    public void testNotDeleted() throws InternalException {
        MockProperties props = new MockProperties();
        props.setMockProperty("facility.list", "LILS");
        props.setMockProperty("facility.LILS.icatUrl", "https://localhost:8181");
        props.setMockProperty("facility.LILS.idsUrl", "https://localhost:8181");
        props.setMockProperty("facility.LILS.maxCartAgeDays.anon", "30");
        props.setMockProperty("facility.LILS.maxCartAgeDays.authenticated", "30");
        new CartChecker("anon/anon", new FacilityMap(props), cartRepository).poll();
        assertNotNull(cartRepository.getCart(anonUserName, facilityName));
        assertNotNull(cartRepository.getCart(authenticatedUserName, facilityName));
    }
}
