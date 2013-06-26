package org.icatproject.topcat.admin.server.ejb.session;

import java.util.ArrayList;

import java.util.List;

import javax.annotation.PostConstruct;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gwt.rpc.client.ast.ReturnCommand;

import uk.ac.stfc.topcat.core.gwt.module.TAuthentication;
import uk.ac.stfc.topcat.core.gwt.module.TFacility;
import uk.ac.stfc.topcat.core.gwt.module.exception.TopcatException;
import uk.ac.stfc.topcat.ejb.entity.TopcatIcatAuthentication;
import uk.ac.stfc.topcat.ejb.entity.TopcatIcatServer;
import uk.ac.stfc.topcat.ejb.manager.UtilityManager;

@Stateless
public class AdminEJB {

	final static Logger logger = LoggerFactory.getLogger(AdminEJB.class);

	@PersistenceContext(unitName = "TopCATEJBPU")
	private EntityManager entityManager;

	@PostConstruct
	private void init() {
		try {
			logger.debug("Initialised AdminEJB");
		} catch (Exception e) {
			String msg = e.getClass().getName() + " reports " + e.getMessage();
			logger.error(msg);
			throw new RuntimeException(msg);
		}
	}

	public void printTopcatIcatServerDetails() {
		@SuppressWarnings("unchecked")
		List<TopcatIcatServer> servers = entityManager.createNamedQuery(
				"TopcatIcatServer.findAll").getResultList();
		for (TopcatIcatServer icatServer : servers) {
			logger.debug(icatServer.getId() + " " + icatServer.getName() + " "
					+ icatServer.getServerUrl() + " " + icatServer.getVersion());
		}
	}

	public ArrayList<TFacility> getAllFacilities() {
		UtilityManager utilityManager = new UtilityManager();
		ArrayList<TFacility> allFacilities = utilityManager
				.getAllFacilities(entityManager);
		for (TFacility facility : allFacilities) {
			logger.debug("getAllFacility :" + " Name: " + facility.getName()
					+ " PluginName:  " + facility.getUrl() + " Version: "
					+ facility.getVersion());

		}
		return allFacilities;
	}

	public void addIcatServer(TFacility facility) throws TopcatException {

		TopcatIcatServer tiServer = new TopcatIcatServer();
		tiServer.setName(facility.getName());
		tiServer.setVersion(facility.getVersion());
		tiServer.setServerUrl(facility.getUrl());
		tiServer.setPluginName(facility.getSearchPluginName());
		tiServer.setDownloadPluginName(facility.getDownloadPluginName());
		tiServer.setDownloadServiceUrl(facility.getDownloadServiceUrl());
		entityManager.persist(tiServer);

		logger.debug("A new row has been added");
	}

	public void updateIcatServer(TFacility facility) {

		TopcatIcatServer tiServer = new TopcatIcatServer();
		tiServer = entityManager.find(TopcatIcatServer.class, facility.getId());
		tiServer.setName(facility.getName());
		tiServer.setVersion(facility.getVersion());
		tiServer.setServerUrl(facility.getUrl());
		tiServer.setPluginName(facility.getSearchPluginName());
		tiServer.setDownloadPluginName(facility.getDownloadPluginName());
		tiServer.setDownloadServiceUrl(facility.getDownloadServiceUrl());
		entityManager.merge(tiServer);

		logger.debug("The Row with the ID: " + facility.getId()
				+ " has been Removed");
	}

	public void removeIcatServer(Long id, String facilityName) {

		List<TopcatIcatAuthentication> authenDetails = entityManager
				.createNamedQuery("TopcatIcatAuthentication.findByServerName")
				.setParameter("serverName", facilityName).getResultList();

		if (!authenDetails.isEmpty()) {
			for (TopcatIcatAuthentication authDetatils : authenDetails) {
				entityManager.remove(authDetatils);
			}
		}

		TopcatIcatServer tiServer = entityManager.find(TopcatIcatServer.class,
				id);
		entityManager.remove(tiServer);

	}

	public List<TAuthentication> authCall(String serverName) {
		ArrayList<TAuthentication> authenticationDetails = new ArrayList<TAuthentication>();
		List<TopcatIcatAuthentication> authenDetails = entityManager
				.createNamedQuery("TopcatIcatAuthentication.findByServerName")
				.setParameter("serverName", serverName).getResultList();
		for (TopcatIcatAuthentication authentication : authenDetails) {
			TAuthentication tAuthentication = new TAuthentication();
			tAuthentication.setType(authentication.getAuthenticationType());
			tAuthentication.setPluginName(authentication.getPluginName());
			tAuthentication
					.setUrl(authentication.getAuthenticationServiceUrl());
			tAuthentication.setId(authentication.getId());
			tAuthentication.setDisplayName(authentication.getDisplayName());
			authenticationDetails.add(tAuthentication);
		}

		return authenticationDetails;
	}

	public void updateAuthDetails(TAuthentication authentication, long id) {
		logger.debug("its updating");
		TopcatIcatAuthentication tiServer = new TopcatIcatAuthentication();
		tiServer = entityManager.find(TopcatIcatAuthentication.class, id);
		tiServer.setAuthenticationType(authentication.getType());
		tiServer.setAuthenticationServiceUrl(authentication.getUrl());
		tiServer.setPluginName(authentication.getPluginName());
		tiServer.setDisplayName(authentication.getDisplayName());
		entityManager.merge(tiServer);
	}

	public void removeRowFromAuthTable(Long id) {
		TopcatIcatAuthentication authonticationDetails = entityManager.find(
				TopcatIcatAuthentication.class, id);
		entityManager.remove(authonticationDetails);
	}

	public void addRowToAuthTable(TAuthentication authentication) {
		TopcatIcatAuthentication authenticationDetails = new TopcatIcatAuthentication();
		authenticationDetails.setAuthenticationType((authentication.getType()));
		authenticationDetails.setAuthenticationServiceUrl((authentication
				.getUrl()));
		authenticationDetails.setPluginName((authentication.getPluginName()));
		authenticationDetails.setDisplayName(authentication.getDisplayName());
		logger.debug(" " + authentication.getId());
		authenticationDetails.setServerId(entityManager.find(
				TopcatIcatServer.class, authentication.getId()));
		entityManager.persist(authenticationDetails);

	}

	public int icatAssociatedAuthCount(String serverName) {
		int count = entityManager
				.createNamedQuery("TopcatIcatAuthentication.findByServerName")
				.setParameter("serverName", serverName).getResultList().size();
		return count;
	}

}
