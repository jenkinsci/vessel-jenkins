package org.jenkinsci.plugins.vessel;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Map;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.ssl.HttpSecureProtocol;
import org.apache.commons.ssl.TrustMaterial;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * A Vessel beta distribution plugin
 * @see official docs @ https://www.vessel.io/resources/
 */
public class VesselUploader {
	
  final String ApiPath = "https://vessel.io/api3/deploy/upload/";
  
	PrintStream logger = null;
	
    static class UploadRequest {
        String apiKey;
        String releaseNotes;
        File file;
        Boolean replace;
        
        // Optional:
        String userGroups;
        String mapping;
        String users;
        
        // Proxy Settings:
        String proxyHost;
        String proxyUser;
        String proxyPass;
        int proxyPort;
    }

    public VesselUploader(PrintStream logger) {
      this.logger = logger;
    }

    public VesselUploader() {
      // TODO Auto-generated constructor stub
    }

    private HttpClient getSSLHttpClient() {
      try {
        HttpSecureProtocol f = new HttpSecureProtocol();

        byte [] caRaw = SSLCertificateStore.getCA();
        
        if (caRaw != null) {
          TrustMaterial ca =  new TrustMaterial(caRaw);
          f.addTrustMaterial(ca);
        }
        caRaw = SSLCertificateStore.getSubclass();
        if (caRaw != null) {
          TrustMaterial ca2 =  new TrustMaterial(caRaw);
          f.addTrustMaterial(ca2);       
        }
        
        Protocol trustHttps = new Protocol("https",f, 443);
        Protocol.registerProtocol("https", trustHttps);
        HttpClient client = new HttpClient();
        return client;
      } catch (GeneralSecurityException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
      return null;
    }
    
    public VesselResponse upload(UploadRequest ur) throws IOException, org.json.simple.parser.ParseException {

        HttpClient httpClient = getSSLHttpClient();
        
        // Configure the proxy if necessary
        if(ur.proxyHost!=null && !ur.proxyHost.isEmpty() && ur.proxyPort>0) {
            HostConfiguration config = httpClient.getHostConfiguration();
            config.setProxy(ur.proxyHost, ur.proxyPort);
            Credentials cred = null;
            if(ur.proxyUser!=null && !ur.proxyUser.isEmpty()) {
                cred = new UsernamePasswordCredentials(ur.proxyUser, ur.proxyPass);
                httpClient.getState().setProxyCredentials(new AuthScope(ur.proxyHost, ur.proxyPort),cred);
            }
        }

        PostMethod postMethod = new PostMethod(ApiPath);
        ArrayList<Part> parts = new ArrayList<Part>();
        
        parts.add(new StringPart("api_key", ur.apiKey));
        parts.add(new StringPart("releasenotes", ur.releaseNotes));
        parts.add(new StringPart("replace", (ur.replace != null && ur.replace) ? "true" : "false"));
        parts.add(new FilePart("file", ur.file));
        
        if (ur.users != null && ur.users.length() > 0) {
          parts.add(new StringPart("users", ur.users));
        }
        
        if (ur.userGroups != null && ur.userGroups.length() > 0) {
          parts.add(new StringPart("groups", ur.userGroups));
        }
        
        if (ur.mapping != null && ur.mapping.length() > 0) {
          parts.add(new StringPart("mapping", ur.mapping));
        }
            
        Part []partsArray = new Part[parts.size()];
        
        partsArray = parts.toArray(partsArray);
        MultipartRequestEntity requestEntity = new MultipartRequestEntity(partsArray, postMethod.getParams());
        postMethod.setRequestEntity(requestEntity);
        
        int statusCode  = httpClient.executeMethod(postMethod);
        
        if (statusCode == HttpStatus.SC_OK) {
          InputStream response = postMethod.getResponseBodyAsStream();
          return parseVesselResponse(response);
        } else {
          String responseString = postMethod.getResponseBodyAsString();
          throw new UploadException(statusCode, responseString, null);
        }
        
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

    /**
    // Useful for quick testing as well
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

    */
    /**  Useful for testing */
    public static void main(String[] args) throws Exception {
        
    	VesselUploader uploader = new VesselUploader();
        
        UploadRequest r = new UploadRequest();
        
        r.apiKey = args[0];
        r.releaseNotes = args[1];
        
        File file = new File(args[2]);
        r.file = file;
        
        if (args.length > 4)
        	r.userGroups = args[3];
        
        if (args.length > 5)
        	r.mapping = args[4];
        
        VesselResponse resp = uploader.upload(r);
        System.out.println(resp.toString());
    }
}
