/**
 *
 * Copyright (c) 2009-2013
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the distribution.
 * Neither the name of the STFC nor the names of its contributors may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 */
package uk.ac.stfc.topcat.ejb.manager;

import java.util.Date;
import java.util.logging.Logger;

import javax.persistence.EntityManager;

import uk.ac.stfc.topcat.core.gwt.module.exception.TopcatException;
import uk.ac.stfc.topcat.ejb.entity.TopcatUserDownload;
import uk.ac.stfc.topcat.ejb.entity.TopcatUserSession;

/**
 * This has admin such as getting list of facilities etc.
 */

public class AdminManager {

    private final static Logger logger = Logger.getLogger(AdminManager.class.getName());

    public void addMyDownload(EntityManager manager, String sessionId, String facilityName, Date submitTime,
            String downloadName, String status, Date expiryTime, String url, String preparedId) throws TopcatException {
        TopcatUserSession userSession = UserManager.getValidUserSessionByTopcatSessionAndServerName(manager, sessionId,
                facilityName);
        TopcatUserDownload download = new TopcatUserDownload();
        download.setName(downloadName);
        download.setUrl(url);
        download.setPreparedId(preparedId);
        download.setStatus(status);
        download.setSubmitTime(submitTime);
        download.setUserId(userSession.getUserId());
        download.setExpiryTime(expiryTime);
        manager.persist(download);
    }

    public void updateDownloadStatus(EntityManager manager, String sessionId, String facilityName, String url,
            String updatedUrl, String status) {
        manager.createNamedQuery("TopcatUserDownload.updateStatus").setParameter("url", url)
                .setParameter("updatedUrl", updatedUrl).setParameter("status", status).executeUpdate();
        manager.flush();
    }

}
