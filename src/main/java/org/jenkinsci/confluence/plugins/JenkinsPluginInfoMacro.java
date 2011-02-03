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
import com.atlassian.confluence.json.parser.JSONObject;
import com.atlassian.confluence.json.parser.JSONException;
import com.atlassian.confluence.json.parser.JSONArray;

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
        String pluginId = (String)parameters.get("pluginId");
        if (pluginId == null) {
            pluginId = (String)parameters.get("0"); // Accept pluginId value without "pluginId="
        }
        if (pluginId == null) { 
            return "No plugin specified.";
        }

        String jiraComponent = (String) parameters.get("jiraComponent");
        String sourceDir = (String) parameters.get("sourceDir");
        
        try {
            HttpResponse response = httpRetrievalService.get("http://updates.hudson-labs.org/update-center.json");
            if (response.getStatusCode() != 200) {
                return subRenderer.render("h4. Plugin Information\n"
                                          + "{warning:title=Cannot Load Update Center}\n"
                                          + "error " + response.getStatusCode() + " loading update-center.json\n"
                                          + "{warning}\n", renderContext);
            }
            
            String rawUpdateCenter = IOUtils.toString(response.getResponse()).trim();
            if (rawUpdateCenter.startsWith("updateCenter.post(")) {
                rawUpdateCenter = rawUpdateCenter.substring("updateCenter.post(".length());
            }
            if (rawUpdateCenter.endsWith(");")) {
                rawUpdateCenter = rawUpdateCenter.substring(0,rawUpdateCenter.lastIndexOf(");"));
            }

            JSONObject updateCenter = new JSONObject(rawUpdateCenter);
            
            StringBuilder toBeRendered = null;
            for (String pluginKey : JSONObject.getNames(updateCenter.getJSONObject("plugins"))) {
                if (pluginKey.equals(pluginId)) {
                    JSONObject pluginJSON = updateCenter.getJSONObject("plugins").getJSONObject(pluginKey);
                    
                    String name = getString(pluginJSON, "name");

                    if (jiraComponent == null) {
                        jiraComponent = name;
                    }
                    boolean isGithub = getString(pluginJSON, "scm").endsWith("github.com"); // Default to svn
                    if (sourceDir == null) {
                        sourceDir = name + (isGithub ? "-plugin" : "");
                    }

                    String releaseTimestamp = getString(pluginJSON, "releaseTimestamp");
                    String fisheyeBaseUrl = "http://fisheye.hudson-labs.org/search/hudson"
                        + "/trunk/hudson/plugins/" + sourceDir
                        + "?ql=select%20revisions%20from%20dir%20/trunk/hudson/plugins/"
                        + sourceDir + "%20where%20date%20>%20";
                    String fisheyeEndUrl = "%20group%20by%20changeset"
                        + "%20return%20csid,%20comment,%20author,%20path";
                    String githubBaseUrl = "https://github.com/hudson/" + sourceDir + "/compare/" + name + "-";
                    String version = getString(pluginJSON, "version");
                    
                    toBeRendered = new StringBuilder("h4. Plugin Information\n"
                                                     + "|| Plugin ID | " + name + " |\n"
                                                     + "|| Latest Release | " + version + " |\n"
                                                     + "|| Latest Release Date | " + getString(pluginJSON, "buildDate") + " |\n"
                                                     + "|| Changes | [In Latest Release|");
                    if (isGithub) {
                        String prevVer = getString(pluginJSON, "previousVersion");
                    	toBeRendered.append(githubBaseUrl + prevVer + "..." + name + "-" + version
                    								 + "]\n[Since Latest Release|" + githubBaseUrl + version
                    								 + "...master]");
                    } else {
                    	toBeRendered.append(fisheyeBaseUrl
                                                     + getString(pluginJSON, "previousTimestamp")
                                                     + "%20and%20date%20<%20" + releaseTimestamp + fisheyeEndUrl
                                                     + "]\n[Since Latest Release|" + fisheyeBaseUrl
                                                     + releaseTimestamp + fisheyeEndUrl + "]");
                    }
                    toBeRendered.append(" |\n|| Maintainer(s) | ");

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

                            devString.append(" (id: " + devId + ")");
                        }
                    }

                    if (devString.length()==0) {
                        devString.append("(not specified)");
                    }

                    toBeRendered.append(devString.toString()).append(" |\n");
                    
                    toBeRendered.append("|| Issue Tracking | [Open Issues|"
                                        + "http://issues.hudson-ci.org/secure/IssueNavigator.jspa?mode=hide&reset=true&jqlQuery=project+%3D+HUDSON+AND+status+in+%28Open%2C+%22In+Progress%22%2C+Reopened%29+AND+component+%3D+'")
                        .append(jiraComponent).append("'] |\n");
                }
            }

            if (toBeRendered==null) {
                toBeRendered = new StringBuilder("h4. Plugin Information\n");
                toBeRendered.append("|| No Information For This Plugin ||\n");
            }

            return subRenderer.render(toBeRendered.toString(), renderContext);
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
