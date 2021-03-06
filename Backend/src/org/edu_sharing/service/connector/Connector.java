package org.edu_sharing.service.connector;

import java.util.List;

public class Connector {
	
	private String id;
	
	private String icon;
	
	private boolean showNew;
	
	private String url;
	
	/**
	 * optional, create element if an empty node is created
	 * Currently only used for h5p connector
	 */
	private String defaultCreateElement;
	
	private List<String> parameters;
	
	private List<ConnectorFileType> filetypes;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getIcon() {
		return icon;
	}

	public void setIcon(String icon) {
		this.icon = icon;
	}

	public boolean isShowNew() {
		return showNew;
	}

	public void setShowNew(boolean showNew) {
		this.showNew = showNew;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public List<String> getParameters() {
		return parameters;
	}

	public void setParameters(List<String> parameters) {
		this.parameters = parameters;
	}

	public List<ConnectorFileType> getFiletypes() {
		return filetypes;
	}

	public void setFiletypes(List<ConnectorFileType> filetypes) {
		this.filetypes = filetypes;
	}

	public String getDefaultCreateElement() {
		return defaultCreateElement;
	}

	public void setDefaultCreateElement(String defaultCreateElement) {
		this.defaultCreateElement = defaultCreateElement;
	}
}
