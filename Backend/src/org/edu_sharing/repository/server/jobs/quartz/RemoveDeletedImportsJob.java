/**
 *
 *  
 * 
 * 
 *	
 *
 * 
 * 
 * 
 * 
 * 
 * 
 * 
 * 
 * 
 * 
 * 
 * 
 * 
 * 
 * 
 * 
 * 
 * 
 *
 */
package org.edu_sharing.repository.server.jobs.quartz;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.edu_sharing.repository.server.importer.ImportCleaner;
import org.edu_sharing.repository.server.importer.PersistentHandlerEdusharing;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;


public class RemoveDeletedImportsJob extends AbstractJob {
	
	
	Logger logger = Logger.getLogger(RemoveDeletedImportsJob.class);
	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		logger.info("start");
		try {
			
			Map jobDataMap = context.getJobDetail().getJobDataMap();
			
			String oaiBaseUrl = (String)jobDataMap.get(OAIConst.PARAM_OAI_BASE_URL);
			if(oaiBaseUrl == null || oaiBaseUrl.trim().equals("")){
				logger.warn("no oaiBaseUrl configured. will do nothing. end");
				return;
			}
			
			Object catalogsParamObj = jobDataMap.get(OAIConst.PARAM_OAI_CATALOG_IDS);
			if(catalogsParamObj == null){
				logger.warn("no allowed catalogs configured. will do nothing. end");
				return;
			}
			
			String metadataPrefix = (String)jobDataMap.get(OAIConst.PARAM_OAI_METADATA_PREFIX);
			
			//String setsParam = (String) jobDataMap.get("sets");
			List<String> catalogsList = null;
			if(catalogsParamObj instanceof String){
				catalogsList = new ArrayList(Arrays.asList(((String)catalogsParamObj).split(",")));
			}else{
				catalogsList = (List)catalogsParamObj;
			}
			
			
			
			HashMap<String, HashMap<String, Object>>  allNodes = new PersistentHandlerEdusharing().getAllNodesInImportfolder();
			new ImportCleaner(oaiBaseUrl, catalogsList, metadataPrefix).removeDeletedImportedObjects(allNodes);
			
		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
			e.printStackTrace();
		}
		logger.info("end");
	}
	
	@Override
	public Class[] getJobClasses() {
		// TODO Auto-generated method stub
		return allJobs;
	}
	
	
}
