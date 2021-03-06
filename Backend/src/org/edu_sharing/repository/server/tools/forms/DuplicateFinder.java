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
package org.edu_sharing.repository.server.tools.forms;

import java.util.HashMap;
import java.util.Map;

import org.edu_sharing.repository.client.tools.CCConstants;
import org.edu_sharing.repository.server.RepoFactory;

public class DuplicateFinder {
	
	/**
	 * finds out if the value for property is taken by another node in nodes
	 * @param nodes
	 * @param property
	 * @param value
	 * @return
	 */
	public boolean valueForPropIsTaken(HashMap<String,HashMap<String,Object>> nodes, String property, String value){
		for(Map.Entry<String,HashMap<String,Object>> entryNode : nodes.entrySet()){
			HashMap<String,Object> properties = entryNode.getValue();
			for(Map.Entry<String,Object> entryProp : properties.entrySet()){
				
				if(entryProp.getKey().equals(property) && entryProp.getValue() != null && entryProp.getValue().equals(value)){
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * finds out if the value for property is taken by another node in nodes.
	 * the currentNodeId is ignored. use this method for updates
	 * 
	 * @param nodes
	 * @param currentNodeId
	 * @param property
	 * @param value
	 * @return
	 */
	public boolean valueForPropIsTaken(HashMap<String,HashMap<String,Object>> nodes, String currentNodeId, String property, String value){
		for(Map.Entry<String,HashMap<String,Object>> entryNode : nodes.entrySet()){
			
			//ignore yourself ;-)
			if(entryNode.getKey().equals(currentNodeId)) continue;
			
			HashMap<String,Object> properties = entryNode.getValue();
			for(Map.Entry<String,Object> entryProp : properties.entrySet()){
				
				if(entryProp.getKey().equals(property) && entryProp.getValue() != null && entryProp.getValue().equals(value)){
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * 
	 * @param nodes nodes to check
	 * @param currentNodeId nodeid to ignore(when its an update)
	 * @param property the property to check
	 * @param value the value to check
	 * @return
	 */
	public String getUniqueValue(HashMap<String,HashMap<String,Object>> nodes, String currentNodeId, String property, String value){
		int cm_name_exists_counter = 2;
		
		String[] splitted = value.split("\\.");
		
		String value0 = (splitted.length > 0 )? splitted[0] : value;
		String result = value;
		
		while(valueForPropIsTaken(nodes, currentNodeId, CCConstants.CM_NAME, result)){
			result = value0 + cm_name_exists_counter;
			if(splitted.length > 1){
				for(int i = 1; i < splitted.length;i++){
					result = result +"."+splitted[i];
				}
			}
			cm_name_exists_counter++;
		}
		
		return result;
	}
	
	public String getUniqueValue(HashMap<String,HashMap<String,Object>> nodes, String property, String value){
		int cm_name_exists_counter = 2;
		
		String[] splitted = value.split("\\.");
		String value0 = (splitted.length > 0 )? splitted[0] : value;
		String result = value;
		while(valueForPropIsTaken(nodes, CCConstants.CM_NAME, result)){
			result = value0 + cm_name_exists_counter;
			if(splitted.length > 1){
				for(int i = 1; i < splitted.length;i++){
					result = result +"."+splitted[i];
				}
			}
			cm_name_exists_counter++;
		}
		
		return result;
	}
	
	/**
	 * replaces CM_NAME prop with an unique and clean safeable name
	 * @param currentLevelObjects
	 * @param propsToSafe
	 */
	public void transformToSafeName(HashMap<String, HashMap<String, Object>> currentLevelObjects, HashMap<String, Object> propsToSafe){
				
		String cmNameReadableName = (String) propsToSafe.get(CCConstants.CM_NAME);

		// only when there is an title
		if (cmNameReadableName != null) {

			// replace ie fakepath like C:\fakepath\test.png
			String replaceFakePathPrefixRegEx = "^[A-Za-z]:\\\\fakepath\\\\";
			String fakePaceCleanedString = cmNameReadableName.replaceFirst(replaceFakePathPrefixRegEx, "");
			if (fakePaceCleanedString.length() > 0) {
				cmNameReadableName = fakePaceCleanedString;
			}

			// replace chars that can lead to an
			// org.alfresco.repo.node.integrity.IntegrityException
			cmNameReadableName = cmNameReadableName.replaceAll(
					RepoFactory.getEdusharingProperty(CCConstants.EDU_SHARING_PROPERTIES_PROPERTY_VALIDATOR_REGEX_CM_NAME), "_");

			//replace ending dot with nothing
			cmNameReadableName = cmNameReadableName.replaceAll("[\\.]*$", "");
			
			cmNameReadableName = new DuplicateFinder().getUniqueValue(currentLevelObjects, CCConstants.CM_NAME, cmNameReadableName);

			propsToSafe.put(CCConstants.CM_NAME, cmNameReadableName);

		}
		
		
	}
}
