package org.icatproject.topcat.repository;

import java.util.ArrayList;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.text.ParseException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import jakarta.ejb.EJB;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Singleton;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import org.icatproject.topcat.domain.Download;
import org.icatproject.topcat.domain.DownloadStatus;
import org.icatproject.topcat.exceptions.BadRequestException;
import org.icatproject.topcat.exceptions.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stateless
@LocalBean
public class DownloadRepository {
	@PersistenceContext(unitName = "topcat")
	EntityManager em;

	private static final Logger logger = LoggerFactory.getLogger(DownloadRepository.class);

	public List<Download> getDownloads(Map<String, Object> params) throws ParseException, BadRequestException {
		List<Download> downloads = new ArrayList<Download>();

		String queryOffset = (String) params.get("queryOffset");
		String userName = (String) params.get("userName");
		Integer limitPageSize = null;
		Integer limitOffset = null;

		if (queryOffset != null) {
			queryOffset = queryOffset.replaceAll("(?i)^\\s*WHERE\\s+", "");
			Pattern pattern = Pattern.compile("(?i)^(.*)LIMIT\\s+(\\d+)\\s*,\\s*(\\d+)\\s*$");
			Matcher matches = pattern.matcher(queryOffset);
			if (matches.find()) {
				queryOffset = matches.group(1);
				limitOffset = Integer.parseInt(matches.group(2));
				limitPageSize = Integer.parseInt(matches.group(3));
			}
		}

		if (em != null) {
			StringBuilder sb = new StringBuilder();
			sb.append("SELECT download FROM Download download ");

			if (userName != null && queryOffset != null) {
				// For GET /user/downloads, userName cannot be null.
				// Do not allow queryOffset to contain closing brackets in this case - could be an exploit attempt
				if( queryOffset.indexOf(')') > -1 ) {
					throw new BadRequestException("downloads queryOffset contains illegal characters");
				}
				sb.append("WHERE download.userName = :userName AND (" + queryOffset + ") ");
			} else if (userName != null) {
				sb.append("WHERE download.userName = :userName ");
			} else if (queryOffset != null) {
				// Note: we believe that this case is only reachable through the GET /admin/downloads/ endpoint;
				// and assume that there is no need to be wary of SQL injection.
				sb.append("WHERE " + queryOffset + " ");
			}

			logger.debug(sb.toString());

			TypedQuery<Download> query = em.createQuery(sb.toString(), Download.class);

			if (userName != null) {
				query.setParameter("userName", userName);
			}

			if (limitOffset != null) {
				query.setFirstResult(limitOffset);
				query.setMaxResults(limitPageSize);
			}

			logger.debug(query.toString());

			downloads = query.getResultList();

			if (downloads != null) {
				return downloads;
			}

		}

		return downloads;
	}

	/**
	 * Get the statuses from downloadIds.
	 * 
	 * @param userName    The formatted userName corresponding to the sessionId that
	 *                    submitted the request
	 * @param downloadIds List of ids to check
	 * @return List of DownloadStatus for each downloadId
	 * @throws NotFoundException If less Downloads are found than ids provided. In
	 *                           practice, this either means the Download(s) do not
	 *                           exist or they did not belong to the user submitting
	 *                           the request.
	 */
	public List<DownloadStatus> getStatuses(String userName, List<Long> downloadIds) throws NotFoundException {
		StringBuilder stringBuilder = new StringBuilder();
		Iterator<Long> downloadIdIterator = downloadIds.iterator();
		stringBuilder.append(downloadIdIterator.next());
		downloadIdIterator.forEachRemaining(downloadId -> {
			stringBuilder.append(",");
			stringBuilder.append(downloadId);
		});
		String queryString = "SELECT download.status FROM Download download WHERE download.userName = '" + userName;
		queryString += "' AND download.id IN (" + stringBuilder.toString() + ")";
		TypedQuery<DownloadStatus> query = em.createQuery(queryString, DownloadStatus.class);
		List<DownloadStatus> resultList = query.getResultList();

		if (resultList.size() < downloadIds.size()) {
			throw new NotFoundException("Could not find a Download for each provided id");
		}

		return resultList;
	}

	public Download getDownload(Long id) {
		return em.find(Download.class, id);
	}

	public Download save(Download store) {
		em.persist(store);
		em.flush();

		return store;
	}

	public void removeDownload(Long id) {
	    Download download = em.find(Download.class, id);
	    if( download != null ){
	        em.remove(download);
		em.flush();
	    }
	}
}
