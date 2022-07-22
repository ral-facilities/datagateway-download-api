package org.icatproject.topcat.domain;

import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public enum EntityType {
    investigation, dataset, datafile
}
