package org.edu_sharing.service.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.search.impl.solr.ESSearchParameters;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.security.authentication.AuthenticationUtil.RunAsWork;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.ResultSetRow;
import org.alfresco.service.cmr.search.SearchParameters;
import org.alfresco.service.cmr.search.SearchParameters.FieldFacet;
import org.alfresco.service.cmr.security.AccessPermission;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.ISO9075;
import org.alfresco.util.Pair;
import org.apache.log4j.Logger;
import org.apache.lucene.queryParser.QueryParser;
import org.edu_sharing.alfresco.policy.Helper;
import org.edu_sharing.alfresco.workspace_administration.NodeServiceInterceptor;
import org.edu_sharing.alfrescocontext.gate.AlfAppContextGate;
import org.edu_sharing.metadataset.v2.MetadataQueries;
import org.edu_sharing.metadataset.v2.MetadataSetV2;
import org.edu_sharing.repository.client.rpc.ACE;
import org.edu_sharing.repository.client.rpc.Authority;
import org.edu_sharing.repository.client.rpc.EduGroup;
import org.edu_sharing.repository.client.rpc.SearchCriterias;
import org.edu_sharing.repository.client.rpc.metadataset.MetadataSet;
import org.edu_sharing.repository.client.rpc.metadataset.MetadataSetQuery;
import org.edu_sharing.repository.client.rpc.metadataset.MetadataSetQueryProperty;
import org.edu_sharing.repository.client.tools.CCConstants;
import org.edu_sharing.repository.client.tools.metadata.search.SearchMetadataHelper;
import org.edu_sharing.repository.server.AuthenticationToolAPI;
import org.edu_sharing.repository.server.MCAlfrescoAPIClient;
import org.edu_sharing.repository.server.SearchResultNodeRef;
import org.edu_sharing.repository.server.tools.ApplicationInfoList;
import org.edu_sharing.restservices.MdsDao;
import org.edu_sharing.restservices.shared.MdsQueryCriteria;
import org.edu_sharing.service.Constants;
import org.edu_sharing.service.InsufficientPermissionException;
import org.edu_sharing.service.permission.PermissionServiceFactory;
import org.edu_sharing.service.permission.PermissionServiceImpl;
import org.edu_sharing.service.search.model.SearchResult;
import org.edu_sharing.service.search.model.SearchToken;
import org.edu_sharing.service.search.model.SortDefinition;
import org.edu_sharing.service.toolpermission.ToolPermissionService;
import org.edu_sharing.service.toolpermission.ToolPermissionServiceFactory;
import org.edu_sharing.service.util.AlfrescoDaoHelper;
import org.springframework.context.ApplicationContext;
import org.springframework.extensions.surf.util.URLEncoder;

public class SearchServiceImpl implements SearchService {

	MCAlfrescoAPIClient apiClient = new MCAlfrescoAPIClient();

	ApplicationContext alfApplicationContext = AlfAppContextGate.getApplicationContext();

	ServiceRegistry serviceRegistry = (ServiceRegistry) alfApplicationContext.getBean(ServiceRegistry.SERVICE_REGISTRY);

	Logger logger = Logger.getLogger(SearchServiceImpl.class);

	private String applicationId;

	org.alfresco.service.cmr.search.SearchService searchService = (org.alfresco.service.cmr.search.SearchService) alfApplicationContext
			.getBean("scopedSearchService");

	private MCAlfrescoAPIClient baseClient;

	private ToolPermissionService toolPermissionService;

	public SearchServiceImpl(String applicationId) {
		this.applicationId = applicationId;
		this.baseClient = new MCAlfrescoAPIClient();
		this.toolPermissionService = ToolPermissionServiceFactory.getInstance();
	}

	@Override
	public List<NodeRef> getFilesSharedByMe() throws Exception {
		String username = AuthenticationUtil.getFullyAuthenticatedUser();
		SearchParameters parameters = new SearchParameters();
		String postfix="";
		if(NodeServiceInterceptor.getEduSharingScope()!=null) {
			postfix+="_"+NodeServiceInterceptor.getEduSharingScope();
		}
		parameters.addStore(Constants.storeRef);
		parameters.setLanguage(org.alfresco.service.cmr.search.SearchService.LANGUAGE_LUCENE);
		parameters.setMaxItems(Integer.MAX_VALUE);
		parameters.addAllAttribute(CCConstants.CCM_PROP_AUTHORITYCONTAINER_EDUHOMEDIR);
		parameters.setQuery("TYPE:\"" + CCConstants.CCM_TYPE_NOTIFY
				+ "\" AND PATH:\"/app\\:company_home/ccm\\:Edu_Sharing_System/ccm\\:Edu_Sharing_Sys_Notify"+postfix+"//.\" AND @cm\\:creator:\"" + QueryParser.escape(username) + "\"");
		ResultSet resultSet = searchService.query(parameters);
		List<NodeRef> refs = convertNotifysToObjects(resultSet.getNodeRefs());
		List<NodeRef> result = new ArrayList<>();
		for (NodeRef node : refs) {
			if (result.contains(node))
				continue;
			try {
				ACE[] permissions = baseClient.getPermissions(node.getId()).getAces();
				if (permissions != null && permissions.length > 0) {
					boolean add = false;
					for (ACE perm : permissions) {
						if (perm.isInherited())
							continue;
						add = true;
						break;
					}
					if (add)
						result.add(node);
				}
			}catch(Throwable t) {
				logger.info("Error fetching permissions for node "+node.getId()+". It's may deleted or the user has no more permissions");
				t.printStackTrace();
			}
		}

		return result;
	}

	private List<NodeRef> convertNotifysToObjects(List<NodeRef> nodeRefs) {
		List<NodeRef> result=new ArrayList<NodeRef>();

		HashSet<QName> types = new HashSet<QName>();
		types.add(QName.createQName(CCConstants.CCM_TYPE_IO));
		types.add(QName.createQName(CCConstants.CCM_TYPE_MAP));

		for (NodeRef nodeRef : nodeRefs) {

			if (nodeRef.getId().contains("missing")) {
				continue;
			}

			List<ChildAssociationRef> childsOfNotify = null;

			try {
				childsOfNotify = serviceRegistry.getNodeService().getChildAssocs(nodeRef, types);
			} catch (org.alfresco.repo.security.permissions.AccessDeniedException e) {
				logger.error(e.getMessage() + " while calling nodeService.getChildAssocs for " + nodeRef);
				continue;
			}
			if (childsOfNotify != null && childsOfNotify.size() > 0) {
				NodeRef ref = childsOfNotify.get(0).getChildRef();
				QName type = serviceRegistry.getNodeService().getType(ref);
				if(types.contains(type)) {
					if(!serviceRegistry.getNodeService().hasAspect(ref,QName.createQName(CCConstants.CCM_ASPECT_COLLECTION)) && !result.contains(ref))
						result.add(ref);
				}
			}
		}
		return result;

	}

	@Override
	public List<NodeRef> getFilesSharedToMe() throws Exception {
		String username = AuthenticationUtil.getFullyAuthenticatedUser();
		String homeFolder = baseClient.getHomeFolderID(username);
		String postfix="";
		if(NodeServiceInterceptor.getEduSharingScope()!=null) {
			postfix+="_"+NodeServiceInterceptor.getEduSharingScope();
		}

		SearchParameters parameters = new SearchParameters();
		parameters.addStore(Constants.storeRef);
		parameters.setLanguage(org.alfresco.service.cmr.search.SearchService.LANGUAGE_LUCENE);
		parameters.setMaxItems(Integer.MAX_VALUE);
		parameters.addAllAttribute(CCConstants.CCM_PROP_AUTHORITYCONTAINER_EDUHOMEDIR);
		// TODO: The amount of files seems to be HUGE, we need a better query for filtering!
		parameters.setQuery("TYPE:\"" + CCConstants.CCM_TYPE_NOTIFY
				+ "\" AND PATH:\"/app\\:company_home/ccm\\:Edu_Sharing_System/ccm\\:Edu_Sharing_Sys_Notify"+postfix+"//.\" AND NOT @cm\\:creator:\"" + QueryParser.escape(username) + "\"");
		ResultSet resultSet = searchService.query(parameters);
		List<NodeRef> result = convertNotifysToObjects(resultSet.getNodeRefs());
		Set<String> memberships = new HashSet<>();
		memberships.addAll(serviceRegistry.getAuthorityService().getAuthorities());
		memberships.remove(CCConstants.AUTHORITY_GROUP_EVERYONE);

		return AuthenticationUtil.runAsSystem(new RunAsWork<List<NodeRef>>() {
			@Override
			public List<NodeRef> doWork() throws Exception {
				List<NodeRef> refs = new ArrayList<>(result.size());
				for (NodeRef node : result) {
					if (refs.contains(node))
						continue;
					if (node.getId().equals(homeFolder))
						continue;
					try {
						ACE[] permissions = baseClient.getPermissions(node.getId()).getAces();
						if (permissions != null && permissions.length > 0) {
							boolean add = false;
							for (ACE perm : permissions) {
								if (!perm.isInherited() && (perm.getAuthority().equals(username) || memberships.contains(perm.getAuthority()))) {
									add = true;
									break;
								}
							}
							if (add)
								refs.add(node);
						}
					}catch(Throwable t) {
						logger.info("Error fetching permissions for node "+node.getId()+". It's may deleted or the user has no more permissions");
						t.printStackTrace();
					}
				}
				return refs;
			}
		});

	}

	@Override
	public SearchResult<EduGroup> getAllOrganizations(boolean scoped) throws Exception {
		return searchOrganizations("", 0, Integer.MAX_VALUE, null,scoped);
	}

	@Override
	public SearchResult<EduGroup> searchOrganizations(String pattern, int skipCount, int maxValues, SortDefinition sort,boolean scoped)
			throws Exception {
		try {
			Set<String> memberships = serviceRegistry.getAuthorityService().getAuthorities();
			return AuthenticationUtil.runAsSystem(new RunAsWork<SearchResult<EduGroup>>() {

				@Override
				public SearchResult<EduGroup> doWork() throws Exception {
					try {
						List<EduGroup> result = new ArrayList<EduGroup>();
						org.alfresco.service.cmr.search.SearchService searchService = serviceRegistry
								.getSearchService();
						SearchParameters parameters = new SearchParameters();
						parameters.addStore(Constants.storeRef);
						parameters.setLanguage(org.alfresco.service.cmr.search.SearchService.LANGUAGE_LUCENE);
						parameters.setMaxItems(Integer.MAX_VALUE);
						parameters.addAllAttribute(CCConstants.CCM_PROP_AUTHORITYCONTAINER_EDUHOMEDIR);
						if (sort != null)
							sort.applyToSearchParameters(parameters);
						String param = QueryParser.escape(pattern == null ? "" : pattern);
						parameters
								.setQuery(
										"(@cm\\:authorityName:\"*" + param + "*\"" + 
										" OR @cm\\:authorityDisplayName:\"*" + param + "*\"" + 
										") AND @ccm\\:edu_homedir:\"workspace://*\"");
						ResultSet edugroups = searchService.query(parameters);

						for (ResultSetRow row : edugroups) {
							HashMap<String, Object> entry = apiClient.getProperties(row.getNodeRef().getId());
							String nodeRef = (String) entry.get(CCConstants.CCM_PROP_AUTHORITYCONTAINER_EDUHOMEDIR);
							// when a group folder relation is removed the noderef can be null cause of async solr refresh
							if (nodeRef != null) {
								String nodeId = nodeRef.replace("workspace://SpacesStore/", "");
								HashMap<String, Object> folderProps = apiClient.getProperties(nodeId);
								EduGroup eduGroup = new EduGroup();
								eduGroup.setFolderId((String) folderProps.get(CCConstants.SYS_PROP_NODE_UID));
								eduGroup.setFolderName((String) folderProps.get(CCConstants.CM_NAME));
								eduGroup.setGroupId((String) entry.get(CCConstants.SYS_PROP_NODE_UID));
								eduGroup.setGroupname((String) entry.get(CCConstants.CM_PROP_AUTHORITY_AUTHORITYNAME));
								eduGroup.setGroupDisplayName(
										(String) entry.get(CCConstants.CM_PROP_AUTHORITY_AUTHORITYDISPLAYNAME));
								eduGroup.setFolderPath(
										apiClient.getPath((String) folderProps.get(CCConstants.SYS_PROP_NODE_UID)));
								eduGroup.setScope((String) folderProps.get(CCConstants.CCM_PROP_EDUSCOPE_NAME));
								boolean add = false;
								for (String group : memberships) {
									if (group.equals(CCConstants.AUTHORITY_GROUP_ALFRESCO_ADMINISTRATORS)
											|| AuthenticationUtil.isRunAsUserTheSystemUser()
											|| group.equals(eduGroup.getGroupname())) {
										add = true;
										break;
									}
								}
								if(scoped) {
									String currentScope = NodeServiceInterceptor.getEduSharingScope();
									if(eduGroup.getScope()==null && currentScope!=null)
										add=false;
									if(eduGroup.getScope()!=null && !eduGroup.getScope().equals(currentScope))
										add=false;
								}
								if (add)
									result.add(eduGroup);
							}
						}
						int count = result.size();
						result = limitList(result, skipCount, maxValues);
						return new SearchResult<EduGroup>(result, skipCount, count);
					} catch (Throwable t) {
						throw new Exception(t);
					}
				}
			});
		} catch (Throwable t) {
			throw t;
		}
	}

	private List limitList(List list, int skipCount, int maxValues) {
		return list.subList(Math.min(skipCount, list.size()), Math.min(list.size(), skipCount + maxValues));
	}
	
	/**
	 * find all parent groups where the given authority is a member
	 */
	@Override
	public SearchResult<String> searchPersonGroups(String authorityName, String pattern, int skipCount, int maxValues, SortDefinition sort) {
		AuthorityService authorityService = serviceRegistry.getAuthorityService();

		return serviceRegistry.getTransactionService().getRetryingTransactionHelper().doInTransaction(

				new RetryingTransactionCallback<SearchResult<String>>() {

					public SearchResult<String> execute() throws Throwable {
						String key = authorityName;
						String[] data = authorityService.getContainingAuthorities(null, key, true)
								.toArray(new String[0]);
						return filterAndSortAuthorities(data,pattern,null,skipCount,maxValues,sort);
					}

				}, true);
	}
	
	@Override
	public SearchResult<String> searchGroupMembers(String groupName, String pattern, String authorityType,
			int skipCount, int maxValues, SortDefinition sort) {
		AuthorityService authorityService = serviceRegistry.getAuthorityService();

		return serviceRegistry.getTransactionService().getRetryingTransactionHelper().doInTransaction(

				new RetryingTransactionCallback<SearchResult<String>>() {

					public SearchResult<String> execute() throws Throwable {
						String key = groupName;
						String[] data = authorityService.getContainedAuthorities(null, key, true)
								.toArray(new String[0]);
						return filterAndSortAuthorities(data,pattern,authorityType,skipCount,maxValues,sort);
					}

				}, true);
	}
	private SearchResult<String> filterAndSortAuthorities(String[] data,String pattern,String authorityType,int skipCount, int maxValues,SortDefinition sort) throws Exception {
		List<String> list2 = new ArrayList<String>();
		if (authorityType != null && !authorityType.isEmpty()) {
			for (String authority : data) {
				if (authorityType.equals("GROUP")
						&& !authority.startsWith(PermissionService.GROUP_PREFIX))
					continue;
				if (authorityType.equals("USER")
						&& authority.startsWith(PermissionService.GROUP_PREFIX))
					continue;
				list2.add(authority);
			}
		} else {
			list2 = Arrays.asList(data);
		}

		List<String> list = new ArrayList<>();
		if (pattern != null && !pattern.isEmpty()) {
			for (String authority : list2) {
				String name = authority;

				if (name.startsWith(PermissionService.GROUP_PREFIX))
					name = name.substring(PermissionService.GROUP_PREFIX.length());
				if (name.toLowerCase().contains(pattern.toLowerCase()))
					list.add(authority);
			}
		} else {
			list = list2;
		}

		if (sort.hasContent()) {
			if (!sort.getFirstSortBy().equals("authorityName")) {
				throw new Exception("Group Members can only be sorted by authorityName, requested: "
						+ sort.getFirstSortBy());
			}
			Collections.sort(list, new Comparator<String>() {
				@Override
				public int compare(String o1, String o2) {
					if (o1.startsWith(PermissionService.GROUP_PREFIX))
						o1 = o1.substring(PermissionService.GROUP_PREFIX.length());
					if (o2.startsWith(PermissionService.GROUP_PREFIX))
						o2 = o2.substring(PermissionService.GROUP_PREFIX.length());
					return o1.compareToIgnoreCase(o2) * (sort.getFirstSortAscending() ? 1 : -1);
				}
			});
		}
		int count = list.size();
		list = limitList(list, skipCount, maxValues);
		return new SearchResult<String>(list, skipCount, count);
	}
	@Override
	public SearchResult<String> searchUsers(String _pattern, boolean globalSearch, int _skipCount, int _maxValues,
			SortDefinition sort,Map<String,String> customProperties) throws Exception {
			return findAuthorities(AuthorityType.USER,_pattern, globalSearch, _skipCount, _maxValues, sort, customProperties);
		/*
		return serviceRegistry.getTransactionService().getRetryingTransactionHelper().doInTransaction(

				new RetryingTransactionCallback<SearchResult<String>>() {
					public SearchResult<String> execute() throws Throwable {
						
						String pattern = _pattern;
						int skipCount = _skipCount;
						int maxValues = _maxValues;
						
						if(restrictAuthoritySearch()) {
							if(pattern != null 
									&& pattern.contains("*") 
									&& pattern.trim().replaceAll(" ", "").replaceAll("\\*", "").length() == 0 ) {
								pattern = pattern.replaceAll("\\*", "");
							}
							skipCount = 0;
							maxValues = 10;
						}
						
						List<String> result = new ArrayList<String>();
						if (globalSearch) {
							checkGlobalSearchPermission();
							PersonService personService = serviceRegistry.getPersonService();
							List<QName> filters = new ArrayList<QName>();
							filters.add(ContentModel.PROP_FIRSTNAME);
							filters.add(ContentModel.PROP_LASTNAME);
							filters.add(ContentModel.PROP_EMAIL);
							PagingRequest paging = new PagingRequest(skipCount, maxValues);
							paging.setRequestTotalCountMax(Integer.MAX_VALUE);
							PagingResults<PersonInfo> peopleReq = personService.getPeople(pattern, filters,
									sort.asSortProperties(), paging);

							try {
								for (PersonInfo personInfo : peopleReq.getPage()) {
									result.add(personInfo.getUserName());
								}
							} catch (IllegalStateException e) {
								// No results found
							}
							return new SearchResult<String>(result, skipCount, peopleReq.getTotalResultCount());

						} else {
							return searchAuthoritiesSolr(pattern, skipCount, maxValues, sort, AuthorityType.USER,false,customProperties);
						}

					}

				}, true);
				*/
	}

	boolean restrictAuthoritySearch() {
		
		if(AuthenticationUtil.isRunAsUserTheSystemUser() 
				|| ApplicationInfoList.getHomeRepository().getUsername().equals(AuthenticationUtil.getRunAsUser())) {
			return false;
		}
		
		/**
		 * find if user is in one ORG_ADMINISTRATORS Group
		 */
		Set<String> authoritiesCurrentUser = serviceRegistry.getAuthorityService().getAuthorities();
		
		try {
			SearchResult<EduGroup> sr = getAllOrganizations(true);
			for(EduGroup eg : sr.getData()) {
				
				if(!authoritiesCurrentUser.contains(eg.getGroupname())) {
					continue;
				}
				
				Set<AccessPermission> aps = serviceRegistry.getPermissionService().getPermissions(new NodeRef(MCAlfrescoAPIClient.storeRef,eg.getFolderId()));
				for(AccessPermission ap : aps) {
					NodeRef authorityNodeRef = serviceRegistry.getAuthorityService().getAuthorityNodeRef(ap.getAuthority());
					String groupType = (String)serviceRegistry.getNodeService().getProperty(authorityNodeRef, QName.createQName(CCConstants.CCM_PROP_GROUPEXTENSION_GROUPTYPE));
					if(groupType != null
							&& groupType.equals(CCConstants.ADMINISTRATORS_GROUP_TYPE)) {
						return false;
					}
				}
			}
			
			return true;
		}catch(Exception e) {
			return true;
		}
		
	}

	protected void checkGlobalSearchPermission() throws InsufficientPermissionException {
		AuthenticationToolAPI authTool = new AuthenticationToolAPI();

		if (authTool.getScope() == null) {
			if ((toolPermissionService.hasToolPermission(CCConstants.CCM_VALUE_TOOLPERMISSION_GLOBAL_AUTHORITY_SEARCH)
					|| toolPermissionService
							.hasToolPermission(CCConstants.CCM_VALUE_TOOLPERMISSION_GLOBAL_AUTHORITY_SEARCH_SHARE)))
				return;
			throw new InsufficientPermissionException(
					"Toolpermission " + CCConstants.CCM_VALUE_TOOLPERMISSION_GLOBAL_AUTHORITY_SEARCH + " or "
							+ CCConstants.CCM_VALUE_TOOLPERMISSION_GLOBAL_AUTHORITY_SEARCH_SHARE + " are missing");
		} else {
			if ((toolPermissionService
					.hasToolPermission(CCConstants.CCM_VALUE_TOOLPERMISSION_GLOBAL_AUTHORITY_SEARCH_SAFE)
					|| toolPermissionService.hasToolPermission(
							CCConstants.CCM_VALUE_TOOLPERMISSION_GLOBAL_AUTHORITY_SEARCH_SHARE_SAFE)))
				return;
			throw new InsufficientPermissionException(
					"Toolpermission " + CCConstants.CCM_VALUE_TOOLPERMISSION_GLOBAL_AUTHORITY_SEARCH_SAFE + " or "
							+ CCConstants.CCM_VALUE_TOOLPERMISSION_GLOBAL_AUTHORITY_SEARCH_SHARE_SAFE + " are missing");
		}
	}

	private SearchResult<String> searchAuthoritiesSolr(String pattern, int skipCount, int maxValues,
			SortDefinition sort, AuthorityType authorityType,boolean globalContext,Map<String,String> customProperties) throws Throwable {
		List<String> result = new ArrayList<>();
		NodeService nodeService = serviceRegistry.getNodeService();
		SearchToken token = new SearchToken();
		String query = "TYPE:cm\\:";
		if (authorityType.equals(AuthorityType.USER))
			query += "person";
		else
			query += "authorityContainer";
		if(customProperties!=null){
			for(Entry<String, String> entry : customProperties.entrySet()){
				query+=" AND @"+entry.getKey().replace(":", "\\:")+":\""+QueryParser.escape(entry.getValue())+"\"";
			}
		}
		query += " AND (@cm\\:authorityName:\"*" + QueryParser.escape(pattern) + "*\" "+
				 "OR @cm\\:userName:\"*" + QueryParser.escape(pattern) + "*\" "+
				 "OR @cm\\:firstName:\"*" + QueryParser.escape(pattern) + "*\" "+
				 "OR @cm\\:lastName:\"*" + QueryParser.escape(pattern) + "*\" "+
				 "OR @cm\\:email:\"*" + QueryParser.escape(pattern) + "*\")";

		if(globalContext){
			checkGlobalSearchPermission();
		}
		else{
			List<EduGroup> organisations = getAllOrganizations(true).getData();
			if (organisations != null && organisations.size() > 0) {
				query += " AND (";
	
				int i = 0;
				for (EduGroup entry : organisations) {
					if (i > 0)
						query += " OR ";
					String ref = StoreRef.STORE_REF_WORKSPACE_SPACESSTORE + "/" + entry.getGroupId();
					// query+="PARENT:"+QueryParser.escape(ref);
					query += "PATH:\""
							+ QueryParser.escape("sys:system/sys:authorities/cm:" + ISO9075.encode(entry.getGroupname()))
							+ "//.\"";
					query += " OR ID:" + QueryParser.escape(ref);
					i++;
				}
				query += ")";
			}
		}
		token.setLuceneString(query);
		token.setFrom(skipCount);
		token.setMaxResult(maxValues);
		token.setSortDefinition(sort);
		token.setContentType(ContentType.ALL);
		token.disableSearchCriterias();
		SearchResultNodeRef data = search(token,false);
		
		for(org.edu_sharing.service.model.NodeRef enr : data.getData()){
			
			NodeRef entry = new NodeRef(new StoreRef(enr.getStoreProtocol(),enr.getStoreId()),enr.getNodeId());
			String name=(String) nodeService.getProperty(entry, ContentModel.PROP_AUTHORITY_NAME);
			if(name==null)
				name=(String)nodeService.getProperty(entry, ContentModel.PROP_USERNAME);
			result.add(name);
		}
		return new SearchResult<String>(result, skipCount, data.getNodeCount());
	}

	@Override
	public SearchResultNodeRef search(MdsDao mdsDao, String query, List<MdsQueryCriteria> criterias,
			SearchToken searchToken) throws Throwable {

		MetadataSet mds = mdsDao.getMetadataSet();
		SearchMetadataHelper smdh = new SearchMetadataHelper();
		
		SearchCriterias scParam = new SearchCriterias();
		scParam.setMetadataSetId(mdsDao.getRef().getId());

		HashMap<MetadataSetQuery, HashMap<MetadataSetQueryProperty, String[]>> searchData = new HashMap<MetadataSetQuery, HashMap<MetadataSetQueryProperty, String[]>>();

		MetadataSetQuery origQuery = null;
		MetadataSetQuery flatQuery = null;
		for (MetadataSetQuery item : mds.getMetadataSetQueries().getMetadataSetQueries()) {
			if (item.getCriteriaboxid().equals(query)) {
				origQuery = item;
				flatQuery = smdh.getFlatCopy(item);
				break;
			}
		}

		if (origQuery == null) {
			throw new Throwable("Query id was not found");
		}

		for (MdsQueryCriteria criteria : criterias) {

			MetadataSetQueryProperty flatQueryProperty = null;
			String property = criteria.getProperty();
			String propertyGlobal = CCConstants.getValidGlobalName(property);
			for (MetadataSetQueryProperty item : origQuery.getProperties()) {
				if (item.getName().equals(property) || item.getName().equals(propertyGlobal)) {
					flatQueryProperty = smdh.getFlatCopy(item, origQuery);
					break;
				}
			}

			if (flatQueryProperty == null) {
				throw new Throwable(
						"could not find property: " + criteria.getProperty() + " in " + origQuery.getCriteriaboxid());
			}

			HashMap<MetadataSetQueryProperty, String[]> props = (searchData.get(flatQuery) == null)
					? new HashMap<MetadataSetQueryProperty, String[]>() : searchData.get(flatQuery);

			props.put(flatQueryProperty, criteria.getValues().toArray(new String[0]));
			searchData.put(flatQuery, props);
		}

		scParam.setMetadataSetSearchData(searchData);

		searchToken.setSearchCriterias(scParam);
		SearchResultNodeRef search = search(searchToken, true);

		return search;

	}
	@Override
	public SearchResultNodeRef searchV2(MetadataSetV2 mds, String query,Map<String,String[]> criterias,
			SearchToken searchToken) throws Throwable {
		MetadataQueries queries = mds.getQueries();
		searchToken.setMetadataQuery(queries.findQuery(query),criterias);
		SearchCriterias scParam = new SearchCriterias();
		scParam.setRepositoryId(mds.getRepositoryId());
		scParam.setMetadataSetId(mds.getId());
		scParam.setMetadataSetQuery(query);
		searchToken.setSearchCriterias(scParam);
		org.edu_sharing.repository.server.authentication.Context.getCurrentInstance().getRequest().getSession().setAttribute(CCConstants.SESSION_LAST_SEARCH_TOKEN, searchToken);
		SearchResultNodeRef search = search(searchToken,true);
		return search;
	}
	@Override
	public SearchToken getLastSearchToken() throws Throwable {
		return (SearchToken) org.edu_sharing.repository.server.authentication.Context.getCurrentInstance().getRequest().getSession().getAttribute(CCConstants.SESSION_LAST_SEARCH_TOKEN);

	}
	public SearchResultNodeRef search(SearchToken searchToken) {
		return search(searchToken, true);
	}

	@Override
	public SearchResultNodeRef search(SearchToken searchToken, boolean scoped) {
		try {
			StoreRef storeRef = new StoreRef(searchToken.getStoreProtocol(), searchToken.getStoreName());

			SearchParameters searchParameters = new ESSearchParameters();
			searchParameters.addStore(storeRef);

			searchParameters.setLanguage(org.alfresco.service.cmr.search.SearchService.LANGUAGE_LUCENE);

			searchParameters.setQuery(searchToken.getLuceneString());
			logger.info("query: "+searchParameters.getQuery());
			searchParameters.setSkipCount(searchToken.getFrom());
			searchParameters.setMaxItems(searchToken.getMaxResult());
			if (searchToken.getSortDefinition() != null)
				searchToken.getSortDefinition().applyToSearchParameters(searchParameters);
			if (searchToken.getContentType().equals(ContentType.FILES) || searchToken.getContentType().equals(ContentType.FILES_AND_FOLDERS)) {
				((ESSearchParameters) searchParameters).setGroupBy("text@sd___@" + CCConstants.CCM_PROP_IO_ORIGINAL);
				// group.truncate = If true, facet counts are based on the most
				// relevant document of each group matching the query. The
				// default value is false.
				// -> only 1 keyword when the file is also in a collection
				// https://cwiki.apache.org/confluence/display/solr/Result+Grouping
				String sort = URLEncoder.encodeUriComponent("datetime@sd@" + CCConstants.CM_PROP_C_CREATED) + "+asc";
				((ESSearchParameters) searchParameters).setGroupConfig(
						"&group=true&group.limit=1&group.sort=" + sort + "&group.ngroups=true&group.truncate=true");
			}
			
			if(searchToken.getAuthorityScope() != null && searchToken.getAuthorityScope().size() > 0) {
				
				if(new Helper(serviceRegistry.getAuthorityService()).isAdmin(serviceRegistry.getAuthenticationService().getCurrentUserName())) {
					((ESSearchParameters) searchParameters).setAuthorities(searchToken.getAuthorityScope().toArray(new String[searchToken.getAuthorityScope().size()]));
				}else {
					logger.error("only admins are allowed to change authority scope of search");
				}
			}

			List<String> facettes = searchToken.getFacettes();
			if (facettes != null && facettes.size() > 0) {
				for (String facetteProp : facettes) {
					String fieldFacette = "@" + facetteProp;
					FieldFacet fieldFacet = new FieldFacet(fieldFacette);
					fieldFacet.setLimit(searchToken.getFacettesLimit());
					fieldFacet.setMinCount(searchToken.getFacettesMinCount());
					searchParameters.addFieldFacet(fieldFacet);
				}
			}
			if (searchToken.getSortDefinition() != null) {
				searchToken.getSortDefinition().applyToSearchParameters(searchParameters);
			}
			ResultSet resultSet;
			if (scoped)
				resultSet = searchService.query(searchParameters);
			else
				resultSet = serviceRegistry.getSearchService().query(searchParameters);

			SearchResultNodeRef sr = new SearchResultNodeRef();
			sr.setData(AlfrescoDaoHelper.unmarshall(resultSet.getNodeRefs(), ApplicationInfoList.getHomeRepository().getAppId()));
			sr.setStartIDX(searchToken.getFrom());
			sr.setNodeCount(searchToken.getMaxResult());
			sr.setNodeCount((int) resultSet.getNumberFound());

			// process facette
			if (facettes != null && facettes.size() > 0) {
				Map<String, Map<String, Integer>> newCountPropsMap = new HashMap<String, Map<String, Integer>>();
				for (String facetteProp : facettes) {
					Map<String, Integer> resultPairs = newCountPropsMap.get(facetteProp);
					if (resultPairs == null) {
						resultPairs = new HashMap<String, Integer>();
					}
					String fieldFacette = "@" + facetteProp;

					List<Pair<String, Integer>> facettPairs = resultSet.getFieldFacet(fieldFacette);
					Integer subStringCount = null;

					// plain solr
					logger.info("found " + facettPairs.size() + " facette pairs for" + fieldFacette);
					for (Pair<String, Integer> pair : facettPairs) {

						// value contains language information i.e. {de}
						String first = pair.getFirst().replaceAll("\\{[a-z]*\\}", "");

						/**
						 * solr4 problem: delivers facetes that have count 0 and
						 * should not occur in the searchresult
						 *
						 * http://stackoverflow.com/questions/10069868/getting-facet-count-0-in-solr
						 * --> pair.getSecond() > 0
						 */
						if (first != null && !first.trim().equals("") && pair.getSecond() > 0) {
							resultPairs.put(first, pair.getSecond());
						}
					}

					if (resultPairs.size() > 0)
						newCountPropsMap.put(facetteProp, resultPairs);
				}
				sr.setCountedProps(newCountPropsMap);

			}

			return sr;

		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
			throw new RuntimeException(e.getMessage());
		}
	}
	@Override
	public List<NodeRef> getWorkflowReceive(String user) {
		SearchParameters parameters = new SearchParameters();
		parameters.addStore(Constants.storeRef);
		parameters.setLanguage(org.alfresco.service.cmr.search.SearchService.LANGUAGE_LUCENE);
		parameters.setMaxItems(Integer.MAX_VALUE);
		parameters.addAllAttribute(CCConstants.CCM_PROP_AUTHORITYCONTAINER_EDUHOMEDIR);

		
		
		Set<String> authoritiesForUser = serviceRegistry.getAuthorityService().getAuthorities();
		String query = "(TYPE:\"" + CCConstants.CCM_TYPE_IO + "\") AND (@ccm\\:wf_receiver:\""+QueryParser.escape(user)+"\"";
		for(String authority : authoritiesForUser) {
			query+=" OR @ccm\\:wf_receiver:\"" + authority + "\"";
		}
		query+=")";
		
		parameters.setQuery(query);
		

		ResultSet resultSet = searchService.query(parameters);
		return resultSet.getNodeRefs();
	}

	@Override
	public SearchResult<String> findAuthorities(AuthorityType type,String searchWord, boolean globalContext, int from, int nrOfResults,SortDefinition sort,Map<String,String> customProperties) throws InsufficientPermissionException {
		if(globalContext)
			checkGlobalSearchPermission();
		HashMap<String, String> toSearch = new HashMap<String, String>();

		// fields to search in - not using username
		toSearch.put("email", searchWord);
		toSearch.put("firstName", searchWord);
		toSearch.put("lastName", searchWord);
		
		PermissionServiceImpl permissionService = (PermissionServiceImpl)PermissionServiceFactory.getPermissionService(null);

		StringBuffer findUsersQuery =  permissionService.getFindUsersSearchString(toSearch, globalContext);
		StringBuffer findGroupsQuery = permissionService.getFindGroupsSearchString(searchWord, globalContext);
		

		/**
		 * don't find groups of scopes when no scope is provided
		 */
		if (NodeServiceInterceptor.getEduSharingScope() == null && findGroupsQuery!=null) {

			/**
			 * groups arent initialized with eduscope aspect and eduscopename
			 * null
			 */
			findGroupsQuery.append(" AND NOT @ccm\\:eduscopename:\"*\"");
		}

		String finalQuery;
		if(type==null) {
			finalQuery="";
			if(findUsersQuery!=null)
				finalQuery += "("+findUsersQuery+")";
			if(findGroupsQuery!=null) {
				finalQuery += " OR (" + findGroupsQuery + ")";
			}
		}
		else if(type.equals(AuthorityType.USER)) {
			finalQuery=findUsersQuery.toString();
		}
		else if(type.equals(AuthorityType.GROUP)) {
			if(findGroupsQuery==null)
				finalQuery="";
			else
				finalQuery=findGroupsQuery.toString();
		}
		else {
			throw new IllegalArgumentException("Unsupported authority type "+type);
		}
		if(finalQuery.isEmpty())
			return new SearchResult<String>();
		if(customProperties!=null){
			for(Map.Entry<String, String> entry : customProperties.entrySet()){
				finalQuery+=(" AND @"+entry.getKey().replace(":", "\\:")+":\""+QueryParser.escape(entry.getValue())+"\"");
			}
		}

		System.out.println("finalQuery:" + finalQuery);

		List<Authority> data = new ArrayList<Authority>();

		SearchParameters searchParameters = new SearchParameters();
		searchParameters.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);

		searchParameters.setLanguage(org.alfresco.service.cmr.search.SearchService.LANGUAGE_FTS_ALFRESCO);
		searchParameters.setQuery(finalQuery.toString());
		searchParameters.setSkipCount(from);
		searchParameters.setMaxItems(nrOfResults);
		if(sort==null || !sort.hasContent()) {
		searchParameters.addSort("@" + CCConstants.CM_PROP_AUTHORITY_AUTHORITYDISPLAYNAME, true);
		searchParameters.addSort("@" + CCConstants.PROP_USER_FIRSTNAME, true);
		}
		else {
			sort.applyToSearchParameters(searchParameters);
		}
		// dont use scopeed search service
		org.alfresco.service.cmr.search.SearchService searchService = serviceRegistry.getSearchService();
		ResultSet resultSet = searchService.query(searchParameters);

		List<String> result = new ArrayList<String>();
		for (NodeRef nodeRef : resultSet.getNodeRefs()) {
			String authorityName = (String) serviceRegistry.getNodeService().getProperty(nodeRef,
					ContentModel.PROP_AUTHORITY_NAME);
			if (authorityName == null) {
				authorityName = (String) serviceRegistry.getNodeService().getProperty(nodeRef,
						ContentModel.PROP_USERNAME);
			}

			result.add(authorityName);
		}

		return new SearchResult<String>(result, from, (int) resultSet.getNumberFound());

	}

}
