package org.icatproject.topcat.repository;

import java.util.List;

import jakarta.ejb.LocalBean;
import jakarta.ejb.Singleton;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import org.icatproject.topcat.domain.ConfVar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stateless
@LocalBean
public class ConfVarRepository {
	@PersistenceContext(unitName = "topcat")
	EntityManager em;

	private static final Logger logger = LoggerFactory.getLogger(ConfVarRepository.class);

	public ConfVar getConfVar(String name){
		TypedQuery<ConfVar> query = em.createQuery("select confVar from ConfVar confVar where confVar.name = :name", ConfVar.class);
		query.setParameter("name", name);
		List<ConfVar> resultList = query.getResultList();
		if(resultList.size() > 0){
			return resultList.get(0);
		} else {
			return null;
		}
	}

	public ConfVar save(ConfVar store) {
		em.persist(store);
		em.flush();

		return store;
	}

}
