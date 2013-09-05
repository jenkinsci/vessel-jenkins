package org.jenkinsci.plugins.vessel;

import hudson.Plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.commons.io.IOUtils;

import jenkins.model.Jenkins;

public class SSLCertificateStore {

  private static byte [] ca = null;
  private static byte [] subclass = null;
  
 // HttpSecureProtocol f = new HttpSecureProtocol();
  //TrustMaterial m = new Tr TrustMaterial(pemBase64);
  
  private static byte [] loadCerts(String name) {
    Jenkins inst = Jenkins.getInstance();
    if (inst == null) {
      return null;
    }
    Plugin plug = inst.getPlugin("vessel");   
    File plugDir =plug.getWrapper().parent.rootDir;
    File caFile = new File(plugDir, name);
    //System.out.println("Trying to load " + caFile.getAbsolutePath());
    try {
      return IOUtils.toByteArray(new FileInputStream(caFile));
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }
  
  public static byte [] getCA() {
    if (ca == null) {
      ca = loadCerts("vessel/ca.pem");
    }
    return ca;
  }
  
  public static byte [] getSubclass() {
    if (subclass == null) {
      subclass = loadCerts("vessel/sub.class1.server.ca.pem");
    }
    return subclass;
  }
}
