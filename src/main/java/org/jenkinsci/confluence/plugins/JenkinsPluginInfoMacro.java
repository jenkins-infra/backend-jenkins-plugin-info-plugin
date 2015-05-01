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
import com.atlassian.confluence.util.http.HttpRetrievalService;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.confluence.plugins.exception.PluginHttpException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

public class JenkinsPluginInfoMacro extends BaseMacro {

    private HttpRetrievalService httpRetrievalService;

    private JenkinsRetriever jenkinsRetriever = new JenkinsRetriever();

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
    
    private String getString(JSONObject o, String prop) {
        if(o.containsKey(prop))
            return o.get(prop).toString();
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
            JSONObject updateCenter = jenkinsRetriever.retrieveUpdateCenterDetails(httpRetrievalService);
            JSONObject plugins = (JSONObject) updateCenter.get("plugins");

            WikiWriter toBeRendered = null;

            if (plugins.containsKey(pluginId)) {
                JSONObject pluginJSON = (JSONObject) plugins.get(pluginId);
                final StatsInfoParser statsParser = getStatsParser(renderContext, pluginId);

                String name = getString(pluginJSON, "name");

                if (jiraComponent == null) {
                    jiraComponent = name;
                }
                if (!jiraComponent.endsWith("-plugin")) {
                    jiraComponent += "-plugin";
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

                toBeRendered = new WikiWriter().h4("Plugin Information");

                {// first row
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
                    /*
                    if (isGithub) {
                        String ciUrl = "https://jenkins.ci.cloudbees.com/job/plugins/job/" + sourceDir;
                        toBeRendered.append("[!").append(ciUrl).append("/badge/icon|border=0!|").append(ciUrl).append("/]");
                    }
                    */

                    toBeRendered.append(" |\n ");
                }

                {// second row
                    String requiredCore = getString(pluginJSON, "requiredCore");
                    toBeRendered.append(" || Latest Release \\\\ Latest Release Date \\\\ Required Core \\\\ Dependencies | ")
                                .href(version,"http://updates.jenkins-ci.org/latest/"+name+".hpi").append(" ").href("(archives)","http://updates.jenkins-ci.org/download/plugins/"+name+"/")
                                .br().append(getString(pluginJSON, "buildDate"))
                                .br().href(requiredCore,"http://updates.jenkins-ci.org/download/war/"+requiredCore+"/jenkins.war")
                                .br().append(getDependencies(updateCenter, pluginJSON))


                                .append(" || Source Code \\\\ Issue Tracking ").append(isGithub ? "\\\\ Pull Requests " : "").append("\\\\ Maintainer(s) | ")
                                .append(isGithub ? "[GitHub|https://github.com/jenkinsci/" : "[Subversion|https://svn.jenkins-ci.org/trunk/hudson/plugins/").append(sourceDir).append(']')
                                .br().append("[Open Issues|http://issues.jenkins-ci.org/secure/IssueNavigator.jspa?mode=hide&reset=true&jqlQuery=project+%3D+JENKINS+AND+status+in+%28Open%2C+%22In+Progress%22%2C+Reopened%29+AND+component+%3D+%27").append(jiraComponent).append("%27]")
                                .br();
                    if (isGithub) {
                        toBeRendered.href("Pull Requests", "https://github.com/jenkinsci/" + sourceDir + "/pulls").br();
                    }

                    WikiWriter devString = new WikiWriter();
                    if (pluginJSON.containsKey("developers")) {
                        JSONArray devArray = (JSONArray)pluginJSON.get("developers");
                        for (int i = 0; i < devArray.size(); i++) {
                            JSONObject developer = (JSONObject)devArray.get(i);
                            String devName = getString(developer, "name");
                            String devId = getString(developer, "developerId");
                            String devEmail = getString(developer, "email");

                            if (devString.length()>0) {
                                devString.append("\n");
                            }

                            if (!devEmail.equals("n/a")) {
                                devString.href(devName,"mailto:" + devEmail);
                            }
                            else {
                                devString.append(devName);
                            }

                            devString.print(" (id: %s)", devId);
                        }
                    }

                    if (devString.length()==0) {
                        devString.append("(not specified)");
                    }
                    toBeRendered.append(devString.toString());
                    toBeRendered.append(" |\n ");
                }

                {// third row
                    if(statsParser != null) {
                        toBeRendered.append(" || Usage | ").image(statsParser.renderChartUrl(true));
                        toBeRendered.append(" || Installations | ");
                        final SortedMap<Date, Integer> sortedSeries = statsParser.getSortedSeries();

                        SimpleDateFormat df = new SimpleDateFormat("yyyy-MMM");
                        for (Entry<Date, Integer> serie : sortedSeries.entrySet()) {
                            toBeRendered.append(df.format(serie.getKey())).append(' ').append(serie.getValue().toString()).append('\n');
                        }
                        toBeRendered.append("[(?)|Plugin Installation Statistics]");
                        toBeRendered.append("|\n");
                    }
                }
            }

            if (toBeRendered==null) {
                toBeRendered = new WikiWriter().h4("Plugin Information");
                toBeRendered.append("|| No Information For This Plugin ||\n");
            } 
            
            return subRenderer.render(toBeRendered.toString(), renderContext);
        }
        catch (IOException e) {
            return subRenderer.render("h4. Plugin Information\n"
                                      + "{warning:title=Cannot Load Update Center}\n"
                                      + "IOException: " + e.getMessage() + "\n"
                                      + "{warning}\n", renderContext);
        } catch (PluginHttpException e) {
            return subRenderer.render("h4. Plugin Information\n"
                    + "{warning:title=Cannot Load Update Center}\n" + "error "
                    + e.getStatusCode() + " loading update-center.json\n"
                    + "{warning}\n", renderContext);
        } catch (ParseException e) {
            return subRenderer.render("h4. Plugin Information\n"
                                      + "{warning:title=Cannot Load Update Center}\n"
                                      + "ParseException: " + e + "\n"
                                      + "{warning}\n", renderContext);
        }
    }

    private String getDependencies(JSONObject updateCenter, JSONObject pluginJSON) {
        WikiWriter depString = new WikiWriter();
        final JSONArray depArray = (JSONArray) pluginJSON.get("dependencies");
        for (int i = 0; i < depArray.size(); i++) {
            JSONObject dependency = (JSONObject) depArray.get(i);
            String depName = getString(dependency, "name");
            String depVersion = getString(dependency, "version");
            String depOptional = getString(dependency, "optional");
            String depWikiUrl = getWikiUrl(updateCenter, depName);

            if (depString.length()>0)
                depString.br();

            if (depWikiUrl.length() > 0) {
                depString.href(depName, depWikiUrl);
            } else {
                depString.append(depName);
            }

            depString.append(" (version:" + depVersion);
            if (Boolean.parseBoolean(depOptional))
                depString.append(", optional");
            depString.append(")");
        }
        return depString.toString();
    }
    
    private String getWikiUrl(JSONObject updateCenter, String pluginId) {
        JSONObject plugins = (JSONObject)updateCenter.get("plugins");
        JSONObject pluginJSON = (JSONObject)plugins.get(pluginId);
        return pluginJSON == null ? "" : getString(pluginJSON, "wiki");
    }

    private StatsInfoParser getStatsParser(RenderContext renderContext, String pluginId) {
        try {
            String rawStats = jenkinsRetriever.retrieveStatsResponse(httpRetrievalService, pluginId);
            if (StringUtils.isNotBlank(rawStats)) {
                return new StatsInfoParser(pluginId, rawStats);
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
