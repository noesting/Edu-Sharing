package org.edu_sharing.service.rendering;

import java.util.HashMap;
import java.util.Map;

import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.apache.log4j.Logger;
import org.edu_sharing.repository.client.tools.CCConstants;
import org.edu_sharing.repository.server.AuthenticationTool;
import org.edu_sharing.repository.server.MCAlfrescoAPIClient;
import org.edu_sharing.repository.server.MCAlfrescoBaseClient;
import org.edu_sharing.repository.server.RepoFactory;
import org.edu_sharing.repository.server.authentication.Context;
import org.edu_sharing.repository.server.tools.ApplicationInfo;
import org.edu_sharing.repository.server.tools.ApplicationInfoList;
import org.edu_sharing.repository.server.tools.HttpQueryTool;
import org.edu_sharing.service.InsufficientPermissionException;
import org.edu_sharing.service.version.VersionService;

public class RenderingServiceImpl implements RenderingService{

	
	ApplicationInfo appInfo;
	
	HashMap<String,String> authInfo;
	
	MCAlfrescoBaseClient client;
	
	AuthenticationTool authTool;
	
	Logger logger = Logger.getLogger(RenderingServiceImpl.class);
	
	public RenderingServiceImpl(String appId){

		try{
			this.appInfo = ApplicationInfoList.getRepositoryInfoById(appId);
			this.authTool = RepoFactory.getAuthenticationToolInstance(appId);
			
			if((AuthenticationUtil.isRunAsUserTheSystemUser() || "admin".equals(AuthenticationUtil.getRunAsUser())) ) {
				logger.debug("starting in runas user mode");
				this.authInfo = new HashMap<String,String>();
				this.authInfo.put(CCConstants.AUTH_USERNAME, AuthenticationUtil.getRunAsUser());
				this.client = new MCAlfrescoAPIClient();
			}else {
				this.authInfo = this.authTool.validateAuthentication(Context.getCurrentInstance().getCurrentInstance().getRequest().getSession());
				this.client = (MCAlfrescoBaseClient)RepoFactory.getInstance(appId, this.authInfo);
			}
			
			
		}catch(Throwable e){
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public String getDetails(String nodeId,String nodeVersion,Map<String,String> parameters) throws InsufficientPermissionException, Exception{		
		
		if(!this.client.hasPermissions(nodeId,new String[]{CCConstants.PERMISSION_READ})){
			throw new InsufficientPermissionException("no read permission");
		}
		try {
			ApplicationInfo appInfo = ApplicationInfoList.getRepositoryInfoById(this.appInfo.getAppId());
			String renderingServiceUrl = new RenderingTool().getRenderServiceUrl(appInfo, nodeId, AuthenticationUtil.getFullyAuthenticatedUser(),nodeVersion,parameters,RenderingTool.DISPLAY_DYNAMIC);
			logger.debug(renderingServiceUrl);
			return new HttpQueryTool().query(renderingServiceUrl);
		}catch(Throwable t) {
			String repository=VersionService.getVersion(VersionService.Type.REPOSITORY);
			String rs=VersionService.getVersion(VersionService.Type.RENDERSERVICE);
			String info="Repository version "+repository+", Renderservice version "+rs;
			if(repository.equals(rs)) {
				logger.info(info);
				throw t;
			}
			else {
				info+=" do not match";
				logger.warn(info);
				throw new Exception(info,t);
			}
		}
	
	}
}
