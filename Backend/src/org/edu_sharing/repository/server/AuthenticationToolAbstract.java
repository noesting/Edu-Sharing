package org.edu_sharing.repository.server;

import java.util.HashMap;

import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.edu_sharing.repository.client.tools.CCConstants;
import org.edu_sharing.repository.server.authentication.Context;

public abstract class AuthenticationToolAbstract implements AuthenticationTool {

	Logger log = Logger.getLogger(AuthenticationToolAbstract.class);
	
	@Override
	public void storeAuthInfoInSession(String username, String ticket, String authType, HttpSession session) {
		String currentTicket = (String)session.getAttribute(CCConstants.AUTH_TICKET);
		//ivalidate old ticket when it's not the same
		if(currentTicket != null && !currentTicket.trim().equals("") && !currentTicket.equals(ticket)){
			try{
				this.logout(currentTicket);
			}catch(Throwable e){
				e.printStackTrace();
			}
		}
		session.setAttribute(CCConstants.AUTH_USERNAME, username);
		session.setAttribute(CCConstants.AUTH_TICKET, ticket);
		session.setAttribute(CCConstants.AUTH_TYPE, authType);
	}
	
	@Override
	public String getTicketFromSession(HttpSession session) {
		String ticket = (String)session.getAttribute(CCConstants.AUTH_TICKET);
		return ticket;
	}
	
	@Override
	public HashMap<String, String> getAuthentication(HttpSession session) {
		HashMap<String,String> result = new HashMap<String,String>();
		String currentTicket = (String)session.getAttribute(CCConstants.AUTH_TICKET);
		String userName = (String)session.getAttribute(CCConstants.AUTH_USERNAME);
		
		result.put(CCConstants.AUTH_USERNAME, userName);
		result.put(CCConstants.AUTH_TICKET, currentTicket);
		return result;
	}
	
	public String getCurrentUser(){
		if(Context.getCurrentInstance() != null && Context.getCurrentInstance().getRequest() != null){
			HttpSession session = Context.getCurrentInstance().getRequest().getSession();
			if(session != null){
				return (String)session.getAttribute(CCConstants.AUTH_USERNAME);
			}
		}
		return null;
	}
	
	public String getCurrentLocale(){
		HttpSession session = Context.getCurrentInstance().getRequest().getSession();
		String currentLocale = (String)session.getAttribute(CCConstants.AUTH_LOCALE);
		if(currentLocale == null || currentLocale.trim().equals("")) currentLocale = "en_EN";
		return currentLocale;
	}
	public String getCurrentLanguage(){
		return getCurrentLocale().substring(0,2);
	}
}
