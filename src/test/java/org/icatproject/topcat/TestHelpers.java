package org.icatproject.topcat;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.icatproject.topcat.domain.Download;
import org.icatproject.topcat.domain.DownloadItem;
import org.icatproject.topcat.domain.DownloadStatus;
import org.icatproject.topcat.domain.EntityType;
import org.icatproject.topcat.repository.DownloadRepository;

import javax.net.ssl.HttpsURLConnection;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.security.SecureRandom;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

public class TestHelpers {

    public static void installTrustManager() {

        // Create a trust manager that does not validate certificate chains
        // Equivalent to --no-certificate-check in wget
        // Only needed if system does not have access to correct CA keys
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        } };

        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Install the all-trusting host verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (Exception e) {
            System.err.println(e.getClass().getSimpleName() + " setting trust manager: " + e.getMessage());
        }
        // log message
        System.out.println("Trust manager set up successfully");
    }

    public static Download createDummyDownload(String userName, String preparedId, String transport, Boolean isTwoLevel,
			DownloadStatus downloadStatus, Boolean isDeleted, DownloadRepository downloadRepository) {

		// This mocks what UserResource.submitCart() might do.

		String facilityName = "LILS";
		String sessionId = "DummySessionId";
		String fileName = "DummyFilename";
		String fullName = "Dummy Full Name";
		// Note: setting email to null means we won't exercise (or test!) the
		// mail-sending code
		String email = null;

		Download download = new Download();
		download.setSessionId(sessionId);
		download.setFacilityName(facilityName);
		download.setFileName(fileName);
		download.setUserName(userName);
		download.setFullName(fullName);
		download.setTransport(transport);
		download.setEmail(email);
		download.setIsEmailSent(false);
		download.setSize(0);
		download.setIsDeleted(isDeleted);
		download.setPreparedId(preparedId);

		List<DownloadItem> downloadItems = new ArrayList<DownloadItem>();

		for (int i = 0; i <= 2; i++) {
			DownloadItem downloadItem = new DownloadItem();
			downloadItem.setEntityId(10L + i);
			downloadItem.setEntityType(EntityType.dataset);
			downloadItem.setDownload(download);
			downloadItems.add(downloadItem);
		}

		download.setDownloadItems(downloadItems);

		download.setIsTwoLevel(isTwoLevel);

		if (isTwoLevel) {
			download.setStatus(downloadStatus);
		} else {
			download.setStatus(downloadStatus);
		}

		return downloadRepository.save(download);
    }

	public static Download getDummyDownload(Long downloadId, DownloadRepository downloadRepository) {
		try {
			return downloadRepository.getDownload(downloadId);
		} catch (Exception e) {
			return null;
		}
	}

	public static void deleteDummyDownload(Long downloadId, DownloadRepository downloadRepository) {
		if (downloadId != null) {
			downloadRepository.removeDownload(downloadId);
		}
	}
}
