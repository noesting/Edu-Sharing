package org.edu_sharing.repository.server.authentication;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.alfresco.repo.security.authentication.AuthenticationException;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.edu_sharing.alfrescocontext.gate.AlfAppContextGate;
import org.edu_sharing.repository.client.tools.CCConstants;
import org.edu_sharing.repository.client.tools.UrlTool;
import org.edu_sharing.repository.server.AuthenticationToolAPI;
import org.edu_sharing.repository.server.tools.ApplicationInfo;
import org.edu_sharing.repository.server.tools.ApplicationInfoList;
import org.edu_sharing.repository.server.tools.AuthenticatorRemoteAppResult;
import org.edu_sharing.repository.server.tools.AuthenticatorRemoteRepository;
import org.edu_sharing.repository.server.tools.security.SignatureVerifier;
import org.edu_sharing.repository.server.tools.security.Signing;
import org.edu_sharing.service.authentication.oauth2.TokenService;
import org.edu_sharing.service.authentication.oauth2.TokenService.Token;
import org.edu_sharing.service.usage.Usage;
import org.edu_sharing.service.usage.Usage2Service;
import org.edu_sharing.service.usage.UsageException;
import org.springframework.context.ApplicationContext;

import net.sf.acegisecurity.AuthenticationCredentialsNotFoundException;

public class AuthenticationFilterPreview implements javax.servlet.Filter {

	Logger logger = Logger.getLogger(AuthenticationFilterPreview.class);
	private TokenService tokenService;
	
	@Override
	public void init(FilterConfig config) throws ServletException {
		ApplicationContext eduApplicationContext = 
				org.edu_sharing.spring.ApplicationContextFactory.getApplicationContext();

		tokenService = (TokenService) eduApplicationContext.getBean("oauthTokenService");		
	}
	
	
	@Override
	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
		
		
		HttpServletRequest httpServletRequest = (HttpServletRequest)req;
		HttpServletResponse httpServletResponse = (HttpServletResponse)resp;
		
		/**
		 * Ticket Auth from 
		 */
		AuthenticationToolAPI authTool = new AuthenticationToolAPI();
		String ticket = authTool.getTicketFromSession(httpServletRequest.getSession());
		if(ticket==null) ticket = req.getParameter("ticket");
		
		boolean invalidateTicket = false;
		
		ApplicationContext appContext = AlfAppContextGate.getApplicationContext();
		ServiceRegistry serviceRegistry = (ServiceRegistry) appContext.getBean(ServiceRegistry.SERVICE_REGISTRY);
		AuthenticationService authService = serviceRegistry.getAuthenticationService();
		
		String repoId = req.getParameter("repoId");
		
		String accessToken = req.getParameter(CCConstants.REQUEST_PARAM_ACCESSTOKEN);
		
		if (accessToken != null && !accessToken.trim().equals("")) {
			//oAuth
			try {
				
				Token token = tokenService.getToken(accessToken);
				
				if (token != null) {
					
					//validate and set current user
					authTool.storeAuthInfoInSession(
							token.getUsername(), 
							token.getTicket(),
							CCConstants.AUTH_TYPE_OAUTH,
							httpServletRequest.getSession());						
				}			
				
			} catch (Exception ex) {
				ex.printStackTrace();
				httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN,ex.getMessage());
				return;
			}				
			
		} else if(ticket != null && !ticket.trim().equals("")){
		
			try {
				authService.validate(ticket);
			} catch (AuthenticationException e) {
				httpServletResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
				return;
			}
			
			/**
			 * remote preview
			 */
			if(repoId != null && !ApplicationInfoList.getHomeRepository().getAppId().equals(repoId)){
				
				HashMap<String,String> localAuthInfo = new HashMap<String,String>();
				localAuthInfo.put(CCConstants.AUTH_TICKET, ticket);
				localAuthInfo.put(CCConstants.AUTH_USERNAME, authService.getCurrentUserName());
				try{
					AuthenticatorRemoteAppResult rai = new AuthenticatorRemoteRepository().getAuthInfoForApp(localAuthInfo, ApplicationInfoList.getRepositoryInfoById(repoId));
					remotePreview(req, httpServletResponse, repoId, rai.getAuthenticationInfo().get(CCConstants.AUTH_TICKET));
				}catch(Throwable e){
					e.printStackTrace();
					httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
				}
				
				return;
			}
			
		
		}else{
			
			//auth by usage and signature
			//the repository the where content is stored
			
			//the proxy Repository
			String proxyRepId = req.getParameter("proxyRepId");
			String sig = req.getParameter("sig");
			if(sig == null || sig.trim().isEmpty()){
				httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST,"missing signature parameter (sig)");
				return;
			}
			sig = sig.trim();
			//sig= URLDecoder.decode(sig);
			String ts = req.getParameter("ts");
			
			//usage data
			String appId = req.getParameter("appId");
			String courseId =  req.getParameter("courseId");
			String nodeId = req.getParameter("nodeId");
			String resourceId = req.getParameter("resourceId");
			
			//signed data
			//String signed = appId + courseId + nodeId + resourceId + ts;
			
			/**
			 * when an remote LMS wants to get an object preview from this repo the proxy repo sends signed data
			 */
			String signed = req.getParameter("signed");
			if(signed == null) signed = appId + ts;
			
			if(repoId == null || repoId.trim().equals("")){
				httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST,"missing repId");
				return;
			}
			
			/**
			 * verify the signature
			 */
			ApplicationInfo tAppInfo = ApplicationInfoList.getRepositoryInfoById(appId);
			if (tAppInfo != null) {
				
				SignatureVerifier.Result result = new SignatureVerifier().verify(appId, sig, signed, ts);
				if(result.getStatuscode() != HttpServletResponse.SC_OK){
					httpServletResponse.sendError(result.getStatuscode(),result.getMessage());
					return;
				}
				
			} else {
				
				if (proxyRepId == null) {
					httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST,"missing proxyRepId");
					return;
				}
				
				SignatureVerifier.Result result = new SignatureVerifier().verify(proxyRepId, sig, signed, ts);
				if(result.getStatuscode() != HttpServletResponse.SC_OK){
					httpServletResponse.sendError(result.getStatuscode(), result.getMessage());
					return;
				}
				
			}
			
			/**
			 * remote preview
			 */
			if (!ApplicationInfoList.getHomeRepository().getAppId().equals(repoId)) {
				remotePreview(req, httpServletResponse, repoId,null);
				return;
			}
			
			/**
			 * local preview check usage
			 */
			Usage2Service u2 = new Usage2Service();
			try{
				
				Usage usage = u2.getUsage(appId, courseId, nodeId, resourceId);

				if(usage == null ){
					httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "No usage Found!");
					return;
				}
	
			} catch(UsageException e) {
				httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
			}
		
			authService.authenticate(ApplicationInfoList.getHomeRepository().getUsername(), ApplicationInfoList.getHomeRepository().getPassword().toCharArray());
			ticket = authService.getCurrentTicket();
			invalidateTicket = true;
			
		}
		
		try{
			chain.doFilter(req, resp);
		} finally {
			
			if (invalidateTicket) {
				authService.invalidateTicket(ticket);
			}else{
				try{
					//its not really necessary cause AuthenticationFilter -> AuthenticationTool calls alfresco authenticationservice.validate which
					//also calls clearCurrentSecurityContext()
					authService.clearCurrentSecurityContext();
				}catch(AuthenticationCredentialsNotFoundException e){
					logger.debug("thread:"+Thread.currentThread().getId() +" "+((HttpServletRequest)req).getServletPath()+ " there was nothing to clean up in native api");
				}
			}
		}
		
	}
	
	private void remotePreview(ServletRequest req, HttpServletResponse httpServletResponse, String rep_id, String remoteTicket) throws IOException{
		
			ApplicationInfo appInfo = ApplicationInfoList.getRepositoryInfoById(rep_id);
			
			String url = appInfo.getClientBaseUrl() + "/preview";
			Map parameterMap = req.getParameterMap();
			for (Object o : parameterMap.entrySet()) {
				Map.Entry entry = (Map.Entry)o;
				String key = (String)entry.getKey();
				String value = null;
				if(entry.getValue() instanceof String[]){
					
					value = ((String[])entry.getValue())[0];
					System.out.println(key+" is "+" string arryay [0]:"+value);
				}else{
					value = (String)entry.getValue();
				}
				
				//leave out the following cause we add our own signature / if ticket we add the new remote ticket
				if (key.equals("sig") || key.equals("signed") || key.equals("ts") || key.equals("ticket")) {
					continue;
				}
				
				url = UrlTool.setParam(url, key, value);
			}
			
			//signature usage auth
			if(remoteTicket == null){
				
				long timestamp = System.currentTimeMillis();
				url = UrlTool.setParam(url, "ts",""+timestamp);
				
				Signing sigTool = new Signing();
				
				String data = rep_id + timestamp;
				url = UrlTool.setParam(url, "signed",""+data);
				
				String privateKey = ApplicationInfoList.getHomeRepository().getPrivateKey();
				
				try {
					if(privateKey != null){
						byte[] signature = sigTool.sign(sigTool.getPemPrivateKey(privateKey, CCConstants.SECURITY_KEY_ALGORITHM), data, CCConstants.SECURITY_SIGN_ALGORITHM);
							
						String urlSig = URLEncoder.encode(new Base64().encodeToString(signature));
						url = UrlTool.setParam(url, "sig",urlSig);
					}
				} catch (GeneralSecurityException e) {
					e.printStackTrace();
					httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				}
				
				if(!url.contains("proxyRepId")){
					url = UrlTool.setParam(url, "proxyRepId",ApplicationInfoList.getHomeRepository().getAppId());
				}
				
			} else {
				url = UrlTool.setParam(url, "ticket", remoteTicket);
			}
			
			httpServletResponse.sendRedirect(url);
			
	}
	
	@Override
	public void destroy() {		
	}

}
