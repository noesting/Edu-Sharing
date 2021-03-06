package org.edu_sharing.alfresco.policy;

import org.alfresco.repo.node.NodeServicePolicies.BeforeDeleteNodePolicy;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.dictionary.InvalidAspectException;
import org.alfresco.service.cmr.repository.InvalidNodeRefException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.version.Version;
import org.alfresco.service.cmr.version.VersionHistory;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.namespace.QName;
import org.apache.log4j.Logger;
import org.edu_sharing.alfresco.service.handleservice.HandleService;
import org.edu_sharing.alfresco.service.handleservice.HandleServiceNotConfiguredException;
import org.edu_sharing.repository.client.tools.CCConstants;

import net.handle.hdllib.HandleException;

public class BeforeDeleteIOPolicy implements BeforeDeleteNodePolicy {
	
	PolicyComponent policyComponent;
	
	NodeService nodeService;
	
	Logger logger = Logger.getLogger(BeforeDeleteIOPolicy.class);
	
	HandleService handleService = null;
	
	VersionService versionService = null;
	
	public void init(){
		policyComponent.bindClassBehaviour(BeforeDeleteNodePolicy.QNAME, QName.createQName(CCConstants.CCM_TYPE_IO), new JavaBehaviour(this, "beforeDeleteNode"));
		
		try {
			handleService = new HandleService();
		} catch (HandleServiceNotConfiguredException e) {
			logger.info(e.getMessage());
		}
	}
	
	@Override
	public void beforeDeleteNode(NodeRef nodeRef) {
		
		if(handleService != null) {
			String handleId = (String)nodeService.getProperty(nodeRef, QName.createQName(CCConstants.CCM_PROP_PUBLISHED_HANDLE_ID));
			if(handleId != null && handleId.trim().length() > 0) {
				try {
					handleService.deleteHandle(handleId);
					nodeService.removeAspect(nodeRef, QName.createQName(CCConstants.CCM_ASPECT_PUBLISHED));
				} catch (HandleException e) {
					logger.error(e.getMessage());
				} catch (Exception e) {
					logger.error(e.getMessage(), e);
				}
			}
			
			VersionHistory vh = versionService.getVersionHistory(nodeRef);
			for(Version v : vh.getAllVersions()) {
				String vhandleId = (String)nodeService.getProperty( v.getFrozenStateNodeRef(), QName.createQName(CCConstants.CCM_PROP_PUBLISHED_HANDLE_ID));
				if(vhandleId != null && vhandleId.trim().length() > 0) {
					
					try {
						handleService.deleteHandle(vhandleId);
					} catch (HandleException e) {
						logger.error(e.getMessage());
					} catch (Exception e) {
						// TODO Auto-generated catch block
						logger.error(e.getMessage(), e);
					}
				}
				
				/**
				 * not able to remove aspect published here cause version can not be manipulated
				 */
			}
		}
	}
	
	
	public void setPolicyComponent(PolicyComponent policyComponent) {
		this.policyComponent = policyComponent;
	}
	
	public void setNodeService(NodeService nodeService) {
		this.nodeService = nodeService;
	}
	
	public void setVersionService(VersionService versionService) {
		this.versionService = versionService;
	}
}
