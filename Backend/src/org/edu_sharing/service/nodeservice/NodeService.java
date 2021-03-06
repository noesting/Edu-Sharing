package org.edu_sharing.service.nodeservice;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.edu_sharing.repository.client.rpc.User;
import org.edu_sharing.repository.server.MCAlfrescoAPIClient;

public interface NodeService {

	
	public void updateNode(String nodeId, HashMap<String, String[]> props) throws Throwable;
	
	public String createNode(String parentId, String nodeType, HashMap<String, String[]> props)throws Throwable;
	
	public String createNode(String parentId, String nodeType, HashMap<String, String[]> props, String childAssociation) throws Throwable;
	
	public String createNodeBasic(String parentID, String nodeTypeString, HashMap<String, Object> _props);

	public String findNodeByName(String parentId, String name );

	public NodeRef copyNode(String sourceId, String nodeId, boolean withChildren) throws Throwable;

	public String getCompanyHome();

	public HashMap<String, String[]> getNameProperty(String name);
	
	public HashMap<String, Object> getChild(StoreRef store, String parentId, String type, String property, String value);
	
	public void setOwner(String nodeId, String username);
	
	public void setPermissions(String nodeId, String authority, String[] permissions, Boolean inheritPermission) throws Exception;

	public String getOrCreateUserInbox();
	
	public String getOrCreateUserSavedSearch();
	
	public List<ChildAssociationRef> getChildrenChildAssociationRef(String parentID);

	public void createVersion(String nodeId, HashMap _properties) throws Exception;
	
	public void writeContent(final StoreRef store, final String nodeID, final InputStream content, final String mimetype, String _encoding,
			final String property) throws Exception;
	
	public void removeNode(String nodeID, String fromID);
	
	public HashMap<String, Object> getProperties(String storeProtocol, String storeId, String nodeId) throws Throwable;

	public InputStream getContent(String storeProtocol, String storeId, String nodeId, String contentProp) throws Throwable;
	
	public String[] getAspects(String storeProtocol, String storeId, String nodeId);
	
	public void addAspect(String nodeId, String aspect);
	
	public void moveNode(String newParentId, String childAssocType, String nodeId);
	
	public void revertVersion(String nodeId, String verLbl) throws Exception;
	
	public HashMap<String, HashMap<String,Object>> getVersionHistory(String nodeId) throws Exception;
	/**
	 * Import the node from a foreign repository to the local one, and return the local node Ref
	 * @param nodeId
	 * @param localParent
	 * @return
	 * @throws Exception 
	 * @throws Throwable 
	 */
	public String importNode(String nodeId,String localParent) throws Throwable;
	
	public User getOwner(String storeId, String storeProtocol, String nodeId);

	public void removeNode(String nodeId, String parentId, boolean recycle);
	
	public void removeNode(String potocol, String store, String nodeId);

	public void removeAspect(String nodeId, String aspect);

	public void updateNodeNative(String nodeId, HashMap<String, Object> _props);

	public void removeProperty(String storeProtocol, String storeId, String nodeId, String property);

	public String getType(String nodeId);

	public boolean exists(String protocol, String store, String nodeId);

	String getProperty(String storeProtocol, String storeId, String nodeId, String property);


	String getTemplateNode(String nodeId,boolean createIfNotExists) throws Throwable;

	/**
	 * Sets the properties for this node's template (inherit metadata to child nodes)
	 * Should only be supported for folder types
	 * @param nodeId
	 * @param stringHashMap
	 */
	void setTemplateProperties(String nodeId, HashMap<String,String[]> stringHashMap) throws Throwable;

	/**
	 * Set if the inherition of properties is enabled for this folder
	 * @param nodeId
	 * @param enable
	 */
	void setTemplateStatus(String nodeId, Boolean enable) throws Throwable;
}
