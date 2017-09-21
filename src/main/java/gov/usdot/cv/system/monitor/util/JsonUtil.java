package gov.usdot.cv.system.monitor.util;

import java.io.FileInputStream;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.map.ObjectMapper;

public class JsonUtil {

	public static ObjectMapper mapper = new ObjectMapper();
	
	public static JSONObject createJsonFromFile(String filePath) {
		JSONObject jsonConfig = null;
    	try {
    		FileInputStream fis = new FileInputStream(filePath);
    		String jsonString = IOUtils.toString(fis);
    		jsonConfig = (JSONObject) JSONSerializer.toJSON(jsonString);
		} catch (Exception ex) {
			System.out.print(String.format("Couldn't create JSONObject from file '%s'.\nReason: %s\n", filePath, ex.getMessage()));
		}
    	
    	return jsonConfig;
	}
}
