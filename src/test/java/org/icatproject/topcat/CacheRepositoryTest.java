package org.icatproject.topcat;

import jakarta.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import org.icatproject.topcat.domain.Cache;
import org.icatproject.topcat.repository.CacheRepository;

@ArquillianTest
public class CacheRepositoryTest {

    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class)
            .addClasses(CacheRepository.class, Cache.class)
            .addAsResource("META-INF/persistence.xml")
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Inject
	private CacheRepository cacheRepository;

	@Test
	public void testPutAndGet() throws Exception {
		cacheRepository.put("test:1", "Hello World!");
		assertEquals( "Hello World!", (String) cacheRepository.get("test:1"));
	}

	@Test
	public void testRemove() {
		String key = "test:remove";
		cacheRepository.put(key, "Hello World");
		cacheRepository.remove(key);
		assertNull(cacheRepository.get(key));
	}
}
