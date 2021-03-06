package org.edu_sharing.restservices.shared;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.HashMap;

import org.edu_sharing.repository.client.tools.CCConstants;
import org.edu_sharing.repository.server.tools.ApplicationInfo;
import org.edu_sharing.repository.server.tools.URLTool;

import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
public class Preview  {
  
  private String url = null;
  private Integer width = null;
  private Integer height = null;
private boolean isIcon;

  public Preview(){
	  
  }
  public Preview(String repositoryType,String node,String storeProtocol,String storeIdentifier,HashMap<String, Object> nodeProps) {
	  if(repositoryType.equals(ApplicationInfo.REPOSITORY_TYPE_ALFRESCO) || repositoryType.equals(ApplicationInfo.REPOSITORY_TYPE_LOCAL)){
		  setUrl(URLTool.getPreviewServletUrl(node,storeProtocol,storeIdentifier));
		  setIsIcon(!(nodeProps.containsKey(CCConstants.CCM_PROP_MAP_ICON) || nodeProps.containsKey(CCConstants.CM_ASSOC_THUMBNAILS)));
	  }
	  else{
		  setUrl((String)nodeProps.get(CCConstants.CM_ASSOC_THUMBNAILS));
		  setIsIcon(false);
	  }
  }
/**
   **/
  @ApiModelProperty(required = true, value = "")
  @JsonProperty("url")
  public String getUrl() {
    return url;
  }
  public void setUrl(String url) {
    this.url = url;
  }

  
  /**
   **/
  @ApiModelProperty(required = true, value = "")
  @JsonProperty("width")
  public Integer getWidth() {
    return width;
  }
  public void setWidth(Integer width) {
    this.width = width;
  }

  
  /**
   **/
  @ApiModelProperty(required = true, value = "")
  @JsonProperty("height")
  public Integer getHeight() {
    return height;
  }
  public void setHeight(Integer height) {
    this.height = height;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class Preview {\n");
    
    sb.append("  url: ").append(url).append("\n");
    sb.append("  width: ").append(width).append("\n");
    sb.append("  height: ").append(height).append("\n");
    sb.append("}\n");
    return sb.toString();
  }
  /**
   **/
  @ApiModelProperty(required = true, value = "")
  @JsonProperty("isIcon")
  public boolean isIcon() {
    return isIcon;
  }
public void setIsIcon(boolean isIcon) {
	this.isIcon=isIcon;
}
}
