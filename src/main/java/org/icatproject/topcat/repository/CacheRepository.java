package org.icatproject.topcat.repository;

import java.util.*;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Singleton;
import jakarta.ejb.Stateless;
import jakarta.ejb.Schedule;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import org.icatproject.topcat.domain.Cache;
import org.icatproject.topcat.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.icatproject.topcat.IcatClient;

import java.io.Serializable;


@LocalBean
@Singleton
public class CacheRepository {
	@PersistenceContext(unitName = "topcat")
	EntityManager em;

	private static final Logger logger = LoggerFactory.getLogger(CacheRepository.class);

	public Object get(String key, Long seconds){
		Cache cache = getCache(key);
		if(cache != null){
			boolean tooOld = false;
			if( seconds > 0 ) {
				// seconds == 0 => immortal (unless pruned through lack of interest)
				// Only do time calcs if we need to.
				long now = new Date().getTime();
				long creation = cache.getCreationTime().getTime();
				tooOld = creation + (seconds * 1000) < now;
			}
			if( ! tooOld ){
				cache.setLastAccessTime(new Date());
				em.persist(cache);
				em.flush();
				return cache.getValue();
			} else {
				em.remove(cache);
				em.flush();
				return null;
			}
		} else {
			return null;
		}
	}

	public Object get(String key){
		return this.get(key,0L);
	}
	
	public void put(String key, Serializable value){
		Cache cache = getCache(key);
		if(cache == null){
			cache = new Cache();
			cache.setKey(key);
		}
		cache.setValue(value);
		em.persist(cache);
		em.flush();
	}
	
	public void remove(String key) {
		Cache cache = getCache(key);
		if( cache != null ){
			em.remove(cache);
		    em.flush();
		}
	}

	@Schedule(hour="*", minute="0")
	public void prune(){
		try {
			Properties properties = Properties.getInstance();
			Integer maxCacheSize = Integer.valueOf(properties.getProperty("maxCacheSize", "10000"));

			TypedQuery<Cache> query = em.createQuery("select cache from Cache cache order by cache.lastAccessTime desc", Cache.class);
			query.setMaxResults(maxCacheSize);

			List<Cache> caches;
			int page = 1;
			while(true){
				query.setFirstResult(maxCacheSize * page);
				caches = query.getResultList();
				if(caches.size() > 0){
					for(Cache cache : caches){
						em.remove(cache);
					}
				} else {
					break;
				}
			}

			em.flush();
		} catch (RuntimeException e) {
			// Catch exceptions to prevent the EJBTimerService from crashing
			logger.error("Unhandled exception in prune()", e);
		}
	}

	private Cache getCache(String key){
		TypedQuery<Cache> query = em.createQuery("select cache from Cache cache where cache.key = :key", Cache.class);
		query.setParameter("key", key);
		List<Cache> resultList = query.getResultList();
		if(resultList.size() > 0){
			return resultList.get(0);
		} else {
			return null;
		}
	}

}
