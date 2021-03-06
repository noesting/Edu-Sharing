package org.edu_sharing.service.config.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlValue;

public class KeyValuePair {
	@XmlAttribute public java.lang.String key;
	@XmlValue public java.lang.String value;
	@Override
	public boolean equals(Object obj) {
		return ((KeyValuePair)obj).key.equals(key);
	}
}
