package org.icatproject.topcat.domain;

import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public enum Status {
    ONLINE, ARCHIVE, RESTORING
}
