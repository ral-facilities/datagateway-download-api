package org.icatproject.topcat.domain;

import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public enum DownloadStatus {
    RESTORING, COMPLETE, EXPIRED, PAUSED, PREPARING, QUEUED
}
