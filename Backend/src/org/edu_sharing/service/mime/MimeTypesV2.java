package org.edu_sharing.service.mime;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.edu_sharing.alfresco.action.RessourceInfoExecuter;
import org.edu_sharing.alfresco.action.RessourceInfoTool;
import org.edu_sharing.repository.client.tools.CCConstants;
import org.edu_sharing.repository.client.tools.Theme;
import org.edu_sharing.repository.server.tools.ApplicationInfo;
import org.edu_sharing.restservices.RepositoryDao;

public class MimeTypesV2 {
	public static final String MIME_DIRECTORY="application/x-directory";

	private static HashMap<String, String> extensionMimeMap;
	private String basePath;
	private String theme;

	public static List<String> WORD=Arrays.asList(new String[]{	
			"application/msword",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.template",
			"application/vnd.ms-word.document.macroEnabled.12",
			"application/vnd.ms-word.template.macroEnabled.12"
			});
	public static List<String> EXCEL=Arrays.asList(new String[]{	
			"application/vnd.ms-excel",
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
			"application/vnd.openxmlformats-officedocument.spreadsheetml.template",
			"application/vnd.ms-excel.sheet.macroEnabled.12",
			"application/vnd.ms-excel.addin.macroEnabled.12",
			"application/vnd.ms-excel.sheet.binary.macroEnabled.12"
			});
	public static List<String> POWERPOINT=Arrays.asList(new String[]{	
			"application/vnd.ms-powerpoint",
			"application/vnd.openxmlformats-officedocument.presentationml.presentation",
			"application/vnd.openxmlformats-officedocument.presentationml.template",
			"application/vnd.openxmlformats-officedocument.presentationml.slideshow",
			"application/vnd.ms-powerpoint.addin.macroEnabled.12",
			"application/vnd.ms-powerpoint.presentation.macroEnabled.12",
			"application/vnd.ms-powerpoint.template.macroEnabled.12",
			"application/vnd.ms-powerpoint.slideshow.macroEnabled.12"
			});
	public static List<String> COMPRESSED=Arrays.asList(new String[]{	
			"application/zip",
			"application/x-zip-compressed",
			"application/x-tar",
			"application/x-ustar",
			"application/x-rar-compressed",
			"application/java-archive"
			});
	public static List<String> SCRIPT=Arrays.asList(new String[]{	
			"application/x-c",
			"text/css",
			"text/html",
			"text/x-java-source"
			});
	
	public MimeTypesV2(ApplicationInfo appInfo){
		this.basePath=appInfo.getClientBaseUrl();
		if(this.basePath.endsWith("/")){
			this.basePath = this.basePath.substring(0, this.basePath.length() - 1);
		}
		if(!this.basePath.startsWith("http") && !this.basePath.startsWith("/")){
			this.basePath = "/" +this.basePath;
		}
		this.theme=Theme.getThemeId();
		if(theme == null){
			theme = CCConstants.THEME_DEFAULT_ID;
		}
		
	}
	public static boolean isDirectory(HashMap<String,Object> properties){
		String type=(String) properties.get(CCConstants.NODETYPE);
		if(type == null) return false;
		return type.equals(CCConstants.CCM_TYPE_MAP) 
			|| type.equals(CCConstants.CM_TYPE_FOLDER);
	}
	public static boolean isCollection(HashMap<String,Object> properties){
		String type=(String) properties.get(CCConstants.CCM_PROP_MAP_COLLECTIONTYPE);
		if(type == null) return false;
		return true;
	}
	/**
	 * Gets the path where the repo stores mime type icons
	 * @return
	 */
	public String getThemePath(){
		return basePath + "/themes/"+theme+"/";
	}
	/**
	 * Gets the path where the repo stores mime type icons
	 * @return
	 */
	public String getIconPath(){
		return getThemePath()+"images/common/mime-types/svg/";
	}
	/**
	 * Gets the path where the repo stores large previews for mime type icons (for replacing the preview)
	 * @return
	 */
	public String getPreviewPath(){
		return getThemePath()+"images/common/mime-types/previews/";
	}
	/**
	 * Gets a full icon path for a small mime icon for the given node properties
	 * @return
	 */
	public String getIcon(String nodeType,HashMap<String,Object> properties,List<String> aspects){
		return getIconPath()+getNodeType(nodeType,properties,aspects)+".svg";
	}
	/**
	 * Gets a full preview path for a large mime image with background for the given node properties
	 * @return
	 */
	public String getPreview(String nodeType,HashMap<String,Object> properties,List<String> aspects){
		return getPreviewPath()+getNodeType(nodeType,properties,aspects)+".svg";
	}
	/**
	 * Gets a default "unknown" preview
	 * @return
	 */
	public String getDefaultPreview(){
		return getPreviewPath()+"file.svg";
	}
	/**
	 * Gets a "Insufficient permissions" preview image
	 * @return
	 */
	public String getNoPermissionsPreview() {
		return getPreviewPath()+"no-permissions.svg";
	}
	/**
	 * Returns the guessed node-type (used for the preview files), e.g. file-folder, file-word or file-image
	 * @param properties
	 * @return
	 */
	public static String getNodeType(String nodeType,HashMap<String,Object> properties,List<String> aspects){
		if(isCollection(properties))
			return "collection";
		if(isDirectory(properties))
			return "folder";
		if(isLtiDefinition(aspects))
			return "tool_definition";
		if(isSavedSearch(nodeType))
			return "saved_search";
		if(isLtiObject(aspects))
			return "tool_object";
		if(isLtiInstance(nodeType))
			return "tool_instance";
		String fallback="file";
		boolean isLink=properties.containsKey(CCConstants.CCM_PROP_IO_WWWURL);
		if(isLink)
			fallback="link";
		return getTypeFromMimetype(getMimeType(properties),properties,fallback);	
	}
	public static String getTypeFromMimetype(String mimetype) {
		return getTypeFromMimetype(mimetype,null,"file");
	}
	private static String getTypeFromMimetype(String mimetype,Map<String,Object> properties,String fallback) {
	if(mimetype==null)
		return fallback;
	
	if(WORD.contains(mimetype))
		return "file-word";
	if(EXCEL.contains(mimetype))
		return "file-excel";
	if(POWERPOINT.contains(mimetype))
		return "file-powerpoint";
	if(COMPRESSED.contains(mimetype)){
		if(properties!=null) {
		String ccressourcetype=(String) properties.get(CCConstants.CCM_PROP_CCRESSOURCETYPE);
			if("imsqti".equals(ccressourcetype))
				return "file-qti";
			if(RessourceInfoExecuter.CCM_RESSOURCETYPE_H5P.equals(ccressourcetype))
				return "file-h5p";
		}
		return "file-zip";
	}
	if(SCRIPT.contains(mimetype))
		return "file-script";
	
	if(mimetype.equals("text/xml"))
		return "file-xml";
	if(mimetype.equals("application/pdf"))
		return "file-pdf";
	if(mimetype.equals("imsqti"))
		return "file-qti";
	if(mimetype.equals("moodle"))
		return "file-moodle";
	if(mimetype.equals("scorm") || mimetype.equals("ADL SCORM"))
		return "file-scorm";
	
	if(mimetype.startsWith("image"))
		return "file-image";
	if(mimetype.startsWith("audio"))
		return "file-audio";
	if(mimetype.startsWith("video"))
		return "file-video";
	if(mimetype.startsWith("text"))
		return "file-txt";
	
	return fallback;
	}
private static boolean isLtiDefinition(List<String> aspects) {
		if(aspects==null)
			return false;
		return aspects.contains(CCConstants.CCM_ASPECT_TOOL_DEFINITION);
	}
private static boolean isSavedSearch(String type) {
	return CCConstants.CCM_TYPE_SAVED_SEARCH.equals(type);
}
private static boolean isLtiInstance(String nodeType) {
	return CCConstants.CCM_TYPE_TOOL_INSTANCE.equals(nodeType);
}
private static boolean isLtiObject(List<String> aspects) {
	if(aspects==null)
		return false;
	return aspects.contains(CCConstants.CCM_ASPECT_TOOL_OBJECT);
}
public static HashMap<String, String> getExtensionMimeMap() {
		
		if(extensionMimeMap == null){
			extensionMimeMap = new HashMap<String,String>();
			extensionMimeMap.put("jpg", "image/jpeg");
			extensionMimeMap.put("jpeg", "image/jpeg");
			extensionMimeMap.put("png", "image/png");
			extensionMimeMap.put("gif", "image/gif");
			extensionMimeMap.put("bmp", "image/bmp");
			extensionMimeMap.put("tiff", "image/tiff");
			extensionMimeMap.put("tif", "image/tif");
			extensionMimeMap.put("psd", "image/photoshop");
			extensionMimeMap.put("xcf", "image/xcf");
			extensionMimeMap.put("pcx", "image/pcx");
			
			extensionMimeMap.put("avi", "video/x-msvideo");
			extensionMimeMap.put("mov", "video/mpeg");
			extensionMimeMap.put("flv", "video/x-flash");
			extensionMimeMap.put("wmv", "video/x-ms-wmv");
			extensionMimeMap.put("mpg", "video/mpeg");
			extensionMimeMap.put("mpeg", "video/mpeg");
			//extensionMimeMap.put("mp4", "video/mp4v-es");
			extensionMimeMap.put("mp4", "video/mp4");
			extensionMimeMap.put("3gp", "video/3gpp");
			
			extensionMimeMap.put("wav", "audio/wav");
			extensionMimeMap.put("mp3", "audio/mpeg");
			extensionMimeMap.put("mid", "audio/mid");
			extensionMimeMap.put("ogg", "audio/ogg");
			extensionMimeMap.put("aif", "audio/aiff");
			extensionMimeMap.put("au", "audio/basic");
			extensionMimeMap.put("vox", "audio/voxware");
			extensionMimeMap.put("wma", "audio/x-ms-wma");
			extensionMimeMap.put("ram", "audio/x-pn-realaudio");
			
			extensionMimeMap.put("odt", "application/vnd.oasis.opendocument.text");
			extensionMimeMap.put("ott", "application/vnd.oasis.opendocument.text-template");
			extensionMimeMap.put("oth", "application/vnd.oasis.opendocument.text-web");
			extensionMimeMap.put("odm", "application/vnd.oasis.opendocument.text-master");
			extensionMimeMap.put("odg", "application/vnd.oasis.opendocument.graphics");
			extensionMimeMap.put("otg", "application/vnd.oasis.opendocument.graphics-template");
			extensionMimeMap.put("odp", "application/vnd.oasis.opendocument.presentation");
			extensionMimeMap.put("otp", "application/vnd.oasis.opendocument.presentation-template");
			extensionMimeMap.put("ods", "application/vnd.oasis.opendocument.spreadsheet");
			extensionMimeMap.put("ots", "application/vnd.oasis.opendocument.spreadsheet-template");
			extensionMimeMap.put("odc", "application/vnd.oasis.opendocument.chart");
			extensionMimeMap.put("odf", "application/vnd.oasis.opendocument.formula");
			extensionMimeMap.put("odb", "application/vnd.oasis.opendocument.database");
			extensionMimeMap.put("odi", "application/vnd.oasis.opendocument.image");
			
			
			extensionMimeMap.put("ppt","application/vnd.ms-powerpoint");
			extensionMimeMap.put("ppz","application/vnd.ms-powerpoint");
			extensionMimeMap.put("pps","application/vnd.ms-powerpoint");
			extensionMimeMap.put("pot","application/vnd.ms-powerpoint");
			
			//docx
			extensionMimeMap.put("doc","application/msword");
			extensionMimeMap.put("docm","application/vnd.ms-word.document.macroEnabled.12");
			extensionMimeMap.put("docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document");
			extensionMimeMap.put("dotm","application/vnd.ms-word.template.macroEnabled.12");
			extensionMimeMap.put("dotx","application/vnd.openxmlformats-officedocument.wordprocessingml.template");
			extensionMimeMap.put("ppsm","application/vnd.ms-powerpoint.slideshow.macroEnabled.12");
			extensionMimeMap.put("ppsx","application/vnd.openxmlformats-officedocument.presentationml.slideshow");
			extensionMimeMap.put("pptm","application/vnd.ms-powerpoint.presentation.macroEnabled.12");
			extensionMimeMap.put("pptx","application/vnd.openxmlformats-officedocument.presentationml.presentation");
			extensionMimeMap.put("xlsb","application/vnd.ms-excel.sheet.binary.macroEnabled.12");
			extensionMimeMap.put("xlsm","application/vnd.ms-excel.sheet.macroEnabled.12");
			extensionMimeMap.put("xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
			extensionMimeMap.put("xps ","application/vnd.ms-xpsdocument");
			
			
			extensionMimeMap.put("dot","application/msword");
			
			extensionMimeMap.put("xls", "application/vnd.ms-excel");
										 
			extensionMimeMap.put("txt", "text/plain");	
			
			extensionMimeMap.put("properties", "text/plain");
			
			extensionMimeMap.put("pdf","application/pdf");
			
			extensionMimeMap.put("zip","application/zip");
			
			extensionMimeMap.put("mbz","application/zip");
			
			extensionMimeMap.put("epub","application/epub+zip");
			
			
			extensionMimeMap.put("xml","text/xml");
			
						
			//apple iworks
			extensionMimeMap.put("pages","application/vnd.apple.pages");
			extensionMimeMap.put("keynote","application/vnd.apple.keynote");
			extensionMimeMap.put("numbers","vnd.apple.numbers");
			
					   
		}
		return extensionMimeMap;
	}
	/**
	 * Get's a mime type from a node, based on the properties. 
	 * If it won't find an alfresco mime type, it will guess it by the file ending
	 * @return
	 */
	public static String getMimeType(HashMap<String, Object> properties) {
		if(isDirectory(properties))
			return MIME_DIRECTORY;
		String mimeType=(String) properties.get(CCConstants.LOM_PROP_TECHNICAL_FORMAT);
		
		if(mimeType==null && properties.containsKey((CCConstants.CM_NAME))){
			String[] name=((String) properties.get(CCConstants.CM_NAME)).split("\\.");
			return getExtensionMimeMap().get(name[name.length-1].toLowerCase());	
		}
		return mimeType;
	}
	
}
