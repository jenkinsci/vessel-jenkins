package org.jenkinsci.plugins.vessel;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.RunList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONObject;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/*
 * https://github.com/derFunk/jenkins-zubhium-plugin
 * 
 * Credits:
 * Inspired by https://github.com/lacostej/testflight-plugin from Jerome Lacoste
 */

public class VesselRecorder extends Recorder
{
    private String apiKey;
    public String getApiKey()
    {
        return this.apiKey;
    }
    
    private String releaseNotes;
    public String getReleaseNotes()
    {
        return this.releaseNotes;
    }
    
    private String apkPath;
    public String getApkPath()
    {
        return this.apkPath;
    }
    
    private Boolean replace;
    public Boolean getReplace()
    {
        return this.replace;
    }
    
    private String userGroups;
    public String getUserGroups()
    {
        return this.userGroups;
    }
    
    private String users;
    public String getUsers()
    {
        return this.users;
    }
    
    private String mapping;
    public String getMapping()
    {
        return this.mapping;
    }
    
    private String proxyHost;
    public String getProxyHost()
    {
        return proxyHost;
    }
    
    private String proxyUser;
    public String getProxyUser()
    {
        return proxyUser;
    }

    private String proxyPass;
    public String getProxyPass()
    {
        return proxyPass;
    }
    
    private int proxyPort;
    public int getProxyPort()
    {
        return proxyPort;
    }

    @DataBoundConstructor
    public VesselRecorder(String apiKey, String releaseNotes, Boolean replace, String apkPath, String userGroups,String proxyHost, String proxyUser, String proxyPass, int proxyPort)
    {
        this.apiKey = apiKey;
        this.releaseNotes = releaseNotes;
        this.apkPath = apkPath;
        this.replace = replace;
        this.userGroups = userGroups;
        
        this.proxyHost = proxyHost;
        this.proxyUser = proxyUser;
        this.proxyPass = proxyPass;
        this.proxyPort = proxyPort;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService( )
    {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
    {
        if (build.getResult().isWorseOrEqualTo(Result.FAILURE))
            return false;

        listener.getLogger().println("Uploading to Vessel");

        File tempDir = null;
        try
        {
            EnvVars vars = build.getEnvironment(listener);

            // Copy remote file to local file system.
            tempDir = File.createTempFile("jzubhium", null);
            tempDir.delete();
            tempDir.mkdirs();
            
            boolean pathSpecified = apkPath != null && !apkPath.trim().isEmpty();
            String expandPath;
            if(!pathSpecified)
            	expandPath = "$WORKSPACE";
            else
            	expandPath = apkPath;
            
            File file = getFileLocally(build.getWorkspace(), vars.expand(expandPath), tempDir, pathSpecified);
            listener.getLogger().println(file);
            
            VesselUploader uploader = new VesselUploader();
            VesselUploader.UploadRequest ur = createUploadRequest(file, vars);

            final VesselResponse vesselResponse;
            try {
                vesselResponse = uploader.upload(ur);
            } catch (UploadException ue) {
                listener.getLogger().println("Incorrect response code: " + ue.getStatusCode());
                listener.getLogger().println(ue.getResponseBody());
                return false;
            }

            if (vesselResponse == null)
            {
            	listener.getLogger().println("Uploading to Vessel was not successful! No answer returned.");
                return false;
            }
            
            // Check for warnings to log
            if (vesselResponse.pushInfo != null){
            	List<String> warnings = new ArrayList<String>();

            	warnings = (List<String>)vesselResponse.pushInfo.get("push.warnings");

            	if (warnings != null)
	            	for (String w : warnings) {
	            		listener.getLogger().println("Vessel WARNING: " + w);
	            	}
            }
            
            // Check for errors to log
            if (vesselResponse.fieldErrors != null)
            {
            	Iterator it = vesselResponse.fieldErrors.entrySet().iterator();
        		while (it.hasNext()) {
					Map.Entry pairs = (Map.Entry)it.next();
					listener.getLogger().println("Vessel ERROR: " + pairs.getKey() + " = " + pairs.getValue());
				}
            }
            
            // Return if no success
            if (vesselResponse.success == false)
            {
                listener.getLogger().println("Uploading to Vessel was not successful!");
                return false;
            }
            else{
            	listener.getLogger().println("Successfully uploaded to Vessel.");
            }
            
            VesselBuildAction installAction = new VesselBuildAction();
            installAction.displayName = "Vessel Install Link";
            installAction.iconFileName = "package.gif";
            installAction.urlName = (String)vesselResponse.pushInfo.get("push.directDownloadUrl");
            build.addAction(installAction);

            /*
            VesselBuildAction configureAction = new VesselBuildAction();
            configureAction.displayName = "Zubhium Social Link";
            configureAction.iconFileName = "star-gold.gif";
            configureAction.urlName = (String)vesselResponse.pushInfo.get("push.socialUrl");
            build.addAction(configureAction);
            */
        }
        catch (Exception e)
        {
            listener.getLogger().println(e);
            e.printStackTrace(listener.getLogger());
            return false;
        }
        finally
        {
            try
            {
                FileUtils.deleteDirectory(tempDir);
            }
            catch (IOException e)
            {
                try
                {
                    FileUtils.forceDeleteOnExit(tempDir);
                }
                catch (IOException e1)
                {
                    listener.getLogger().println(e1);
                }
            }
        }

        return true;
    }

    private VesselUploader.UploadRequest createUploadRequest(File apk, EnvVars vars) {
        VesselUploader.UploadRequest ur = new VesselUploader.UploadRequest();
        ur.apiKey = vars.expand(apiKey);
        ur.releaseNotes = vars.expand(releaseNotes);
        ur.userGroups = vars.expand(userGroups);
        ur.mapping =  vars.expand(mapping);
        ur.file = apk;
        ur.replace = replace;
        
        // Proxy data:
        ur.proxyHost = proxyHost;
        ur.proxyPass = proxyPass;
        ur.proxyPort = proxyPort;
        ur.proxyUser = proxyUser;
        
        return ur;
    }

    private File getFileLocally(FilePath workingDir, String strFile, File tempDir, boolean pathSpecified) throws IOException, InterruptedException
    {
    	if(!pathSpecified) {
    		File workspaceDir = new File(strFile);
    		List<File> ipas = new LinkedList<File>();
    		findIpas(workspaceDir, ipas);
    		if(ipas.isEmpty())
    			return workspaceDir;
    		return ipas.get(0);
    	} else {
			if (workingDir.isRemote())
			{
				FilePath remoteFile = new FilePath(workingDir, strFile);
				File file = new File(tempDir, remoteFile.getName());
				file.createNewFile();
				FileOutputStream fos = new FileOutputStream(file);
				remoteFile.copyTo(fos);
				fos.close();
				return file;
			}
			else
			{
				return new File(strFile);
			}
        }
    }
    
    private void findIpas(File root, List<File> ipas) {
		for(File file : root.listFiles()) {
			if(file.isDirectory())
				findIpas(file, ipas);
			else if(file.getName().endsWith(".ipa"))
				ipas.add(file);
		}
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project)
    {
        ArrayList<VesselBuildAction> actions = new ArrayList<VesselBuildAction>();
        RunList<? extends AbstractBuild<?,?>> builds = project.getBuilds();

        Collection predicated = CollectionUtils.select(builds, new Predicate() {
            public boolean evaluate(Object o) {
                return ((AbstractBuild<?,?>)o).getResult().isBetterOrEqualTo(Result.SUCCESS);
            }
        });

        ArrayList<AbstractBuild<?,?>> filteredList = new ArrayList<AbstractBuild<?,?>>(predicated);


        Collections.reverse(filteredList);
        for (AbstractBuild<?,?> build : filteredList)
        {
           List<VesselBuildAction> testflightActions = build.getActions(VesselBuildAction.class);
           if (testflightActions != null && testflightActions.size() > 0)
           {
               for (VesselBuildAction action : testflightActions)
               {
                   actions.add(new VesselBuildAction(action));
               }
               break;
           }
        }

        return actions;
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher>
    {
        public DescriptorImpl() {
            super(VesselRecorder.class);
            load();
        }
                
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            // XXX is this now the right style?
            req.bindJSON(this,json);
            save();
            return true;
        }
                
        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Upload to Vessel";
        }
    }
}
