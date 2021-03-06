package org.edu_sharing.alfresco.service;

import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.util.MD5;
import org.apache.log4j.Logger;

public class AuthorityService {
	
	public static String ADMINISTRATORS_GROUP_TYPE = "ORG_ADMINISTRATORS";
	
	public static String ADMINISTRATORS_GROUP = "ORG_ADMINISTRATORS";
	public static String ORG_GROUP_PREFIX = "ORG_";
	public static String ADMINISTRATORS_GROUP_DISPLAY_POSTFIX = "_Admins";

	Logger logger = Logger.getLogger(AuthorityService.class);

	

	org.alfresco.service.cmr.security.AuthorityService authorityService;

	public String createOrUpdateGroup(String groupName, String displayName, String parentGroup,
			boolean preventDuplicate) {
		String originalName = AuthorityService.getGroupName(groupName, parentGroup);
		String name = originalName;
		String key = PermissionService.GROUP_PREFIX + name;
		
		if (preventDuplicate) {
			int i = 2;
			while (authorityService.authorityExists(key)) {
				name = originalName + "-" + i;
				key = PermissionService.GROUP_PREFIX + name;
				i++;
			}
		}
		
		if (authorityService.authorityExists(key)) {
			authorityService.setAuthorityDisplayName(key, displayName);
		} else {
			authorityService.createAuthority(AuthorityType.GROUP, name, displayName, authorityService.getDefaultZones());
			if (parentGroup != null) {
				addMemberships(parentGroup, new String[] { key });
			}
		}

		return name;
	}

	public void addMemberships(String groupName, String[] members) {

		String key = PermissionService.GROUP_PREFIX + groupName;

		for (String member : members) {

			if (member == null) {
				continue;
			}

			authorityService.addAuthority(key, member);
		}

	}

	public static String getGroupName(String groupName, String parentGroup) {
		String prefix = "";
		if (parentGroup != null) {
			prefix = MD5.Digest(parentGroup.getBytes()) + "_";
		}
		String name = prefix + groupName;

		return name;
	}

	public void setAuthorityService(org.alfresco.service.cmr.security.AuthorityService authorityService) {
		this.authorityService = authorityService;
	}

}
