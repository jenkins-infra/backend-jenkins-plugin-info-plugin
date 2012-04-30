package org.jenkinsci.confluence.plugins;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.SortedMap;
import java.util.Map.Entry;

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
import org.apache.commons.lang.StringUtils;

public class JenkinsPluginInfoMacro extends BaseMacro {

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
    public String execute(Map parameters, String body, RenderContext renderContext)
            throws MacroException {
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
            HttpResponse response = httpRetrievalService.get("http://updates.jenkins-ci.org/update-center.json");
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
                    final StatsInfoParser statsParser = getStatsParser(renderContext, pluginId);
                    
                    String name = getString(pluginJSON, "name");

                    if (jiraComponent == null) {
                        jiraComponent = name;
                    }
                    boolean isGithub = getString(pluginJSON, "scm").endsWith("github.com"); // Default to svn
                    if (sourceDir == null) {
                        sourceDir = name + (isGithub && !name.endsWith("-plugin") ? "-plugin" : "");
                    }

                    String releaseTimestamp = getString(pluginJSON, "releaseTimestamp");
                    String fisheyeBaseUrl = "http://fisheye.jenkins-ci.org/search/Jenkins"
                        + "/trunk/hudson/plugins/" + sourceDir
                        + "?ql=select%20revisions%20from%20dir%20/trunk/hudson/plugins/"
                        + sourceDir + "%20where%20date%20>%20";
                    String fisheyeEndUrl = "%20group%20by%20changeset"
                        + "%20return%20csid,%20comment,%20author,%20path";
                    String githubBaseUrl = "https://github.com/jenkinsci/" + sourceDir + "/compare/" + name + "-";
                    String version = getString(pluginJSON, "version");
                    
                    toBeRendered = new StringBuilder("h4. Plugin Information\n");
                    toBeRendered.append("|| Plugin ID | ")
                                .append(name)
                                .append(" || Changes | [In Latest Release|");
                    if (isGithub) {
                        String prevVer = getString(pluginJSON, "previousVersion");
                    	toBeRendered.append(githubBaseUrl).append(prevVer)
                                    .append("...").append(name).append('-').append(version)
                                    .append("]\n[Since Latest Release|").append(githubBaseUrl)
                                    .append(version).append("...master]");
                    } else {
                    	toBeRendered.append(fisheyeBaseUrl)
                                    .append(getString(pluginJSON, "previousTimestamp"))
                                    .append("%20and%20date%20<%20").append(releaseTimestamp)
                                    .append(fisheyeEndUrl)
                                    .append("]\n[Since Latest Release|").append(fisheyeBaseUrl)
                                    .append(releaseTimestamp).append(fisheyeEndUrl).append(']');
                    }
                    
                    if(statsParser != null){
                        toBeRendered.append(" || Installations | ");
                        final SortedMap<Date, Integer> sortedSeries = statsParser.getSortedSeries();

                        SimpleDateFormat df = new SimpleDateFormat("yyyy-MMM");
                        for (Entry<Date, Integer> serie : sortedSeries.entrySet()) {
                            toBeRendered.append(df.format(serie.getKey())).append(", ").append(serie.getValue().toString());
                        }
                        toBeRendered.append(" |\n ");
                    }
                    
                    toBeRendered.append(" || Latest Release \\\\ Latest Release Date \\\\ Required Core | ").append(version)
                                .append(" \\\\ ").append(getString(pluginJSON, "buildDate")).append(version)
                                .append(" \\\\ ").append(getString(pluginJSON, "requiredCore"))
                                
                                
                                .append(" || Source Code \\\\ Issue Tracking \\\\ Maintainer(s) | ")
                                .append(isGithub ? "[GitHub|https://github.com/jenkinsci/" : "[Subversion|https://svn.jenkins-ci.org/trunk/hudson/plugins/").append(sourceDir).append(']')
                                .append(" \\\\ [Open Issues|http://issues.jenkins-ci.org/secure/IssueNavigator.jspa?mode=hide&reset=true&jqlQuery=project+%3D+JENKINS+AND+status+in+%28Open%2C+%22In+Progress%22%2C+Reopened%29+AND+component+%3D+'").append(jiraComponent).append(']')
                                .append(" \\\\ ");

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
                    String chartUrl = statsParser == null ? "n/a" : statsParser.renderChartUrl(true);
                    toBeRendered.append(devString.toString()).append(" || Usage | !").append(chartUrl).append("! | ");;
                    break;
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

    private StatsInfoParser getStatsParser(RenderContext renderContext, String pluginId) {
        try {
            HttpResponse statsResponse = httpRetrievalService.get("http://stats.jenkins-ci.org/plugin-installation-trend/" + pluginId + ".stats.json");
            if (statsResponse.getStatusCode() != 200) {
                subRenderer.render("h4. Stats\n" + "{warning:title=Cannot load statistics}\n" + "error " + statsResponse.getStatusCode()
                        + " loading update-center.json\n" + "{warning}\n", renderContext);
            } else {
                String rawStats = IOUtils.toString(statsResponse.getResponse()).trim();
                if (StringUtils.isNotBlank(rawStats)) {
                    return new StatsInfoParser(pluginId, rawStats);
                }
            }
        } catch (Exception e) {
            subRenderer.render("h4. Stats\n" + "{warning:title=Cannot load statistics}\n" + "Exception: " + e.getMessage() + "\n"
                    + "{warning}\n", renderContext);
        }
        return null;
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
