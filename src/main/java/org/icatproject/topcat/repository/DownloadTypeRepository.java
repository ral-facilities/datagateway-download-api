package org.icatproject.topcat.repository;

import java.util.List;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import org.icatproject.topcat.domain.Download;
import org.icatproject.topcat.domain.DownloadType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stateless
@LocalBean
public class DownloadTypeRepository {
	@PersistenceContext(unitName = "topcat")
	EntityManager em;

	private static final Logger logger = LoggerFactory.getLogger(DownloadTypeRepository.class);

	/**
	 * @param facilityName ICAT Facility.name
	 * @return all DownloadTypes for facilityName
	 */
	public List<DownloadType> getDownloadTypes(String facilityName) {
		TypedQuery<DownloadType> query = em.createQuery("select downloadType from DownloadType downloadType where downloadType.facilityName = :facilityName", DownloadType.class);
		query.setParameter("facilityName", facilityName);
		return query.getResultList();
	}

	public DownloadType getDownloadType(String facilityName, String downloadType) {
		TypedQuery<DownloadType> query = em.createQuery("select downloadType from DownloadType downloadType where downloadType.facilityName = :facilityName and downloadType.downloadType = :downloadType", DownloadType.class);
		query.setParameter("facilityName", facilityName);
		query.setParameter("downloadType", downloadType);
		List<DownloadType> resultList = query.getResultList();
		if(resultList.size() > 0){
			return resultList.get(0);
		} else {
			return null;
		}
	}

	public DownloadType save(DownloadType store) {
		em.persist(store);
		em.flush();

		return store;
	}

	/**
	 * @param id DownloadType.id to remove
	 */
	public void remove(Long id) {
	    DownloadType downloadType = em.find(DownloadType.class, id);
	    if (downloadType != null) {
	        em.remove(downloadType);
		em.flush();
	    }
	}
}
