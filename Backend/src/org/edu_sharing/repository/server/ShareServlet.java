package org.edu_sharing.repository.server;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.SingleThreadModel;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.alfresco.model.ContentModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.namespace.QName;
import org.apache.log4j.Logger;
import org.edu_sharing.alfrescocontext.gate.AlfAppContextGate;
import org.edu_sharing.repository.client.rpc.Share;
import org.edu_sharing.repository.client.tools.CCConstants;
import org.edu_sharing.repository.server.tools.ApplicationInfoList;
import org.edu_sharing.repository.server.tools.URLTool;
import org.edu_sharing.service.share.ShareService;
import org.edu_sharing.service.share.ShareServiceImpl;
import org.springframework.context.ApplicationContext;

public class ShareServlet extends HttpServlet implements SingleThreadModel {

	Logger logger = Logger.getLogger(ShareServlet.class);

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		ServletOutputStream op = resp.getOutputStream();

		String token = req.getParameter("token");

		if (token == null) {
			op.println("missing token");
			return;
		}

		String nodeId = req.getParameter("nodeId");

		if (nodeId == null) {
			op.println("missing nodeId");
			return;
		}

		ApplicationContext appContext = AlfAppContextGate.getApplicationContext();

		ServiceRegistry serviceRegistry = (ServiceRegistry) appContext.getBean(ServiceRegistry.SERVICE_REGISTRY);
		AuthenticationService authenticationService = serviceRegistry.getAuthenticationService();
		// authentication
		String adminUser = ApplicationInfoList.getHomeRepository().getUsername();
		String adminPassword = ApplicationInfoList.getHomeRepository().getPassword();

		try {

			authenticationService.authenticate(adminUser, adminPassword.toCharArray());
			
			
			NodeRef nodeRef = new NodeRef(MCAlfrescoAPIClient.storeRef, nodeId);
			if(!serviceRegistry.getNodeService().exists(nodeRef)){
				resp.sendRedirect(URLTool.getNgMessageUrl("share_file_deleted"));
				//op.println("File does not longer exist!");
				return;
			}

			ShareService shareService = new ShareServiceImpl();
			Share share = shareService.getShare(nodeId, token);
			if (share == null) {
				resp.sendRedirect(URLTool.getNgMessageUrl("invalid_share"));
				//op.println("no share found for this nodeid and token!");
				return;
			}

			if (share.getExpiryDate() != ShareService.EXPIRY_DATE_UNLIMITED) {
				if (new Date(System.currentTimeMillis()).after(new Date(share.getExpiryDate()))) {
					resp.sendRedirect(URLTool.getNgMessageUrl("share_expired"));
					//op.println("share is expired!");
					return;
				}
			}
			
			String wwwUrl = (String)serviceRegistry.getNodeService().getProperty(nodeRef,QName.createQName(CCConstants.CCM_PROP_IO_WWWURL));
			if(wwwUrl != null && !wwwUrl.trim().equals("")){
				resp.sendRedirect(wwwUrl);
				return;
			}
			
			String fileName = (String)serviceRegistry.getNodeService().getProperty(nodeRef,QName.createQName(CCConstants.CM_NAME));

			ContentReader reader = serviceRegistry.getContentService().getReader(nodeRef,
					ContentModel.PROP_CONTENT);
			if(reader==null){
				resp.sendRedirect(URLTool.getNgMessageUrl("share_empty"));
				//op.println("The file is empty!");
				return;
			}
			String mimetype = reader.getMimetype();

			resp.setContentType((mimetype != null) ? mimetype : "application/octet-stream");
			resp.setContentLength((int) reader.getContentData().getSize());
			resp.setHeader("Content-Disposition", "attachment; filename=\""+fileName+ "\"");

			int length = 0;
			//
			// Stream to the requester.
			//
			byte[] bbuf = new byte[1024];
			// DataInputStream in = new
			// DataInputStream(url.openStream());
			DataInputStream in = new DataInputStream(reader.getContentInputStream());
			while ((in != null) && ((length = in.read(bbuf)) != -1)) {
				op.write(bbuf, 0, length);
			}

			in.close();
			op.flush();
			op.close();
			
			share.setDownloadCount( (share.getDownloadCount() + 1) );
			shareService.updateShare(share);

		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
		} finally {
			authenticationService.invalidateTicket(authenticationService.getCurrentTicket());
			authenticationService.clearCurrentSecurityContext();
		}

		return;

	}

}
