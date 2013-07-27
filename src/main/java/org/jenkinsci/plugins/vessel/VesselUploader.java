package org.jenkinsci.plugins.vessel;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * A Vessel beta distibution plugin
 * @see official docs @ https://www.vessel.io/resources/
 */
public class VesselUploader {
	
	//final String ApiHost = "www.vesselapp.com";
  final String ApiHost = "localhost";
	final String ApiPath = "/api3/deploy/upload/";
	
	
    static class UploadRequest {
        String apiKey;
        String releaseNotes;
        File file;
        Boolean replace;
        
        // Optional:
        String userGroups;
        String mapping;
        
        // Proxy Settings:
        String proxyHost;
        String proxyUser;
        String proxyPass;
        int proxyPort;
    }

    public VesselResponse upload(UploadRequest ur) throws IOException, org.json.simple.parser.ParseException {

        DefaultHttpClient httpClient = new DefaultHttpClient();

        // Configure the proxy if necessary
        if(ur.proxyHost!=null && !ur.proxyHost.isEmpty() && ur.proxyPort>0) {
            Credentials cred = null;
            if(ur.proxyUser!=null && !ur.proxyUser.isEmpty())
                cred = new UsernamePasswordCredentials(ur.proxyUser, ur.proxyPass);

            httpClient.getCredentialsProvider().setCredentials(new AuthScope(ur.proxyHost, ur.proxyPort),cred);
            HttpHost proxy = new HttpHost(ur.proxyHost, ur.proxyPort);
            httpClient.getParams().setParameter( ConnRoutePNames.DEFAULT_PROXY, proxy);
        }

        HttpHost targetHost = new HttpHost(ApiHost, 443, "https");

        if ("localhost".equals(ApiHost)) {
           targetHost = new HttpHost(ApiHost, 8000, "http");
        }
        HttpPost httpPost = new HttpPost(ApiPath);

        MultipartEntity entity = new MultipartEntity();
        entity.addPart("api_key", new StringBody(ur.apiKey));
        entity.addPart("releasenotes", new StringBody(ur.releaseNotes));
        entity.addPart("file", new FileBody(ur.file));
        entity.addPart("replace", new StringBody((ur.replace != null && ur.replace) ? "true" : "false"));
        
        if (ur.userGroups != null && ur.userGroups.length() > 0)
            entity.addPart("groups", new StringBody(ur.userGroups));
        
        if (ur.mapping != null && ur.mapping.length() > 0)
            entity.addPart("mapping", new StringBody(ur.mapping));
        
        httpPost.setEntity(entity);

        HttpResponse response = httpClient.execute(targetHost,httpPost);
        HttpEntity resEntity = response.getEntity();

        InputStream is = resEntity.getContent();

        // Improved error handling.
        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode != 200) {
            String responseBody = new Scanner(is).useDelimiter("\\A").next();
            throw new UploadException(statusCode, responseBody, response);
        }

        return parseVesselResponse(is);
    }

    //Parsing nested JSON to make life easier
    private static VesselResponse parseVesselResponse(InputStream is) throws ParseException, IOException
    {
         JSONParser parser = new JSONParser();
        
    	 Map responseMap = (Map)parser.parse(new BufferedReader(new InputStreamReader(is)));
         
    	 if (responseMap == null)
    		 return null;

    	 VesselResponse r = new VesselResponse();
    	 
         // check if errors have occurred
         if (responseMap.containsKey("push.errors"))
         {
        	 r.pushErrors = (Map)parser.parse(responseMap.get("push.errors").toString());
        	 
        	 if (r.pushErrors.containsKey("fielderrors")){
        		 r.fieldErrors = (Map)parser.parse(r.pushErrors.get("fielderrors").toString());
        	 }
         }
         
         if (responseMap.containsKey("push.info"))
         	r.pushInfo = (Map)parser.parse(responseMap.get("push.info").toString());
         
         // append success param so we don't lose this information
         if (responseMap.containsKey("success"))
        	 r.success = Boolean.parseBoolean(responseMap.get("success").toString());
         
         return r;
    }

    
    /**  Useful for quick testing as well */
    public static void main(String[] args) throws Exception {
    	JSONParser parser = new JSONParser();
    	String json = "{\"push.info\":{\"push.noUsersInvited\":4,\"push.directDownloadUrl\":\"http://zubhi.co/g/xxx/\",\"push.warnings\":[\"warning1\",\"warning2\"],\"push.versionName\":\"5.0\",\"push.socialUrl\":\"http://zubhi.co/d/xxxx/\",\"push.versionCode\":\"110\",\"push.proguardEnabled\":false,},\"success\":true}";
    	VesselResponse response = parseVesselResponse(new ByteArrayInputStream(json.getBytes()));
    	
    	List<String> warnings = (List<String>)response.pushInfo.get("push.warnings");
    	
    	json = "{\"push.errors\": {\"fielderrors\": {\"apk\": [\"Please increment version code, push with version code 1 already exist\"]}, \"no_errors\": 1}, \"success\": false}";
    	response = parseVesselResponse(new ByteArrayInputStream(json.getBytes()));
    	
    	Iterator it = response.fieldErrors.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pairs = (Map.Entry)it.next();
			System.out.println("Zubhium ERROR: " + pairs.getKey() + " = " + pairs.getValue());
		}
    	
    }

    
    /**  Useful for testing */
    public static void mainCli(String[] args) throws Exception {
        
    	VesselUploader uploader = new VesselUploader();
        
        UploadRequest r = new UploadRequest();
        
        r.apiKey = args[0];
        r.releaseNotes = args[2];
        
        File file = new File(args[3]);
        r.file = file;
        
        if (args.length > 4)
        	r.userGroups = args[4];
        
        if (args.length > 5)
        	r.mapping = args[5];
        
        uploader.upload(r);
    }
}
