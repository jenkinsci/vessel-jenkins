package org.jenkinsci.plugins.vessel;

import java.util.Collection;
import java.util.Map;

public class VesselResponse {

	public Map pushErrors;
	public Map fieldErrors;
	public Map pushInfo;
	public Boolean success;
	
	public String toString() {
	  return "Success : " + success + " pushErrors: " + pushErrors;
	}
}
