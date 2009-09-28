package org.jvnet.hudson.confluence.plugins;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.SubRenderer;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.renderer.v2.macro.MacroException;
import com.atlassian.confluence.util.http.HttpResponse;
import com.atlassian.confluence.util.http.HttpRetrievalService;
import com.atlassian.confluence.json.JSONArray;
import com.atlassian.confluence.json.JSONObject;
import com.atlassian.confluence.json.JSONException;

import org.apache.commons.io.IOUtils;

public class HudsonPluginInfoMacro extends BaseMacro {

    private HttpRetrievalService httpRetrievalService;
    
    private static final String OUTPUT_PARAMETER = "output";
    private static final String OUTPUT_PARAMETER_HTML = "html";
    private static final String OUTPUT_PARAMETER_WIKI = "wiki";
    
    /**
     * Setter method for automatic injection of the {@link HttpRetrievalService}.
     *
     * @param httpRetrievalService the http retrieval service to use
     */
    public void setHttpRetrievalService(HttpRetrievalService httpRetrievalService) {
	this.httpRetrievalService = httpRetrievalService;
    }
    
    /**
     * non inline, so we return false
     */
    public boolean isInline() {
	return false;
    }
    
    /**
     * no body supported, so this returns false
     */
    public boolean hasBody() {
	return false;
    }
    
    /**
     * the execute method takes care of formatting the output,
     * so NO_RENDER is returned
     */
    public RenderMode getBodyRenderMode() {
	return RenderMode.NO_RENDER;
    }
    
    private String getString(JSONObject o, String prop) throws JSONException {
	if(o.has(prop))
	    return o.getString(prop);
	else
	    return "n/a";
    }

    /**
     * includes the given file, formats it (if specified) and
     * returns it.
     * 
     * @param parameters
     * @param body in this case null
     * @param renderContext 
     * @return formatted Content of the specified file (String)
     * 
     */
    public String execute(Map parameters, String body, RenderContext renderContext) throws MacroException {
	String pluginId = (String) parameters.get(0);
	
	if (pluginId == null) {
	    pluginId = (String) parameters.get("pluginId");
	}
	
	if (pluginId == null) { 
	    return "No plugin specified.";
	}

	try {
	    HttpResponse response = httpRetrievalService.get("http://hudson.dev.java.net/update-center.json");
            if (response.getStatusCode() != 200) {
                return subRenderer.render("h4. Plugin Information\n"
					  + "{warning:title=Cannot Load Update Center}\n"
					  + "error " + response.getStatusCode() + " loading update-center.json\n"
					  + "{warning}\n", renderContext);
            }
	    
	    String rawUpdateCenter = IOUtils.toString(response.getResponse()).trim();
	    
	    if (rawUpdateCenter.startsWith("updateCenter.post(")) {
		rawUpdateCenter = rawUpdateCenter.substring(new String("updateCenter.post(").length());
	    }

	    if (rawUpdateCenter.endsWith(");")) {
		rawUpdateCenter = rawUpdateCenter.substring(0,rawUpdateCenter.lastIndexOf(");"));
	    }

	    JSONObject updateCenter;
	    
	    updateCenter = new JSONObject(rawUpdateCenter);
	    
	    String toBeRendered = "";
	    for (String pluginKey : JSONObject.getNames(updateCenter.getJSONObject("plugins"))) {
		if (pluginKey.equals(pluginId)) {
		    JSONObject pluginJSON = updateCenter.getJSONObject("plugins").getJSONObject(pluginKey);
		    
		    toBeRendered = "h4. Plugin Information\n";
		    toBeRendered += "|| Plugin ID | " + getString(pluginJSON, "name") + " |\n";
		    toBeRendered += "|| Latest Release | " + getString(pluginJSON, "version") + " |\n";
		    toBeRendered += "|| Latest Release Date | " + getString(pluginJSON, "buildDate")+ " |\n";
		    toBeRendered += "|| Changes in Latest Release | "
			+ "[via Fisheye|http://fisheye4.atlassian.com/search/hudson/trunk/plugins/"
			+ getString(pluginJSON, "name")
			+ "?ql=select%20revisions%20from%20dir%20/trunk/hudson/plugins/"
			+ getString(pluginJSON, "name")
			+ "%20where%20date%20>%20"
			+ getString(pluginJSON, "previousTimestamp")
			+ "%20and%20date%20<%20"
			+ getString(pluginJSON, "releaseTimestamp")
			+ "%20group%20by%20changeset] |\n";
		    toBeRendered += "|| Maintainer(s) | ";

		    StringBuilder devString = new StringBuilder();
		    
		    if (pluginJSON.has("developers")) {
			JSONArray devArray = pluginJSON.getJSONArray("developers");
			for (int i = 0; i < devArray.length(); i++) {
			    String devName = getString(devArray.getJSONObject(i), "name");
			    String devId = getString(devArray.getJSONObject(i), "developerId");
			    String devEmail = getString(devArray.getJSONObject(i), "email");

			    if (devString.length()>0) {
				devString.append("\n");
			    }

			    if (!devEmail.equals("n/a")) {
				devString.append("[" + devName + "|mailto:" + devEmail + "]");
			    }
			    else {
				devString.append(devName);
			    }

			    devString.append(" (java.net id: " + devId + ")");
			}
		    }

		    if (devString.length()==0) {
			devString.append("(not specified)");
		    }

		    toBeRendered += devString.toString() + " |\n";
                    
                    toBeRendered += "|| Issue Tracking | [Open Issues|https://hudson.dev.java.net/issues/buglist.cgi?component=hudson&issue_status=NEW&issue_status=STARTED&issue_status=REOPENED&subcomponent=" + getString(pluginJSON, "name") + "] |\n";
		}
	    }

	    if (toBeRendered.equals("")) {
		toBeRendered = "h4. Plugin Information\n";
		toBeRendered += "|| No Information For This Plugin ||\n";
	    }

	    return subRenderer.render(toBeRendered, renderContext);
	}
	catch (JSONException e) {
	    return subRenderer.render("h4. Plugin Information\n"
				      + "{warning:title=Cannot Load Update Center}\n"
				      + "JSONException: " + e.getMessage() + "\n"
				      + "{warning}\n", renderContext);
	}

	catch (IOException e) {
	    return subRenderer.render("h4. Plugin Information\n"
				      + "{warning:title=Cannot Load Update Center}\n"
				      + "IOException: " + e.getMessage() + "\n"
				      + "{warning}\n", renderContext);
	}
    }
    
    
    private SubRenderer subRenderer;
    /**
     * @param subRenderer
     *            The subRenderer to set.
     */
    public void setSubRenderer(SubRenderer subRenderer) {
        this.subRenderer = subRenderer;
    }
    
}
