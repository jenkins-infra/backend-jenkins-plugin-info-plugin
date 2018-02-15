package org.jenkinsci.confluence.plugins;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.atlassian.renderer.RenderContext;
import com.atlassian.confluence.renderer.PageContext;
import com.atlassian.confluence.labels.Label;
import com.atlassian.confluence.core.ContentEntityObject;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.SubRenderer;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.renderer.v2.macro.MacroException;
import com.atlassian.confluence.util.http.HttpRetrievalService;

import org.jenkinsci.confluence.plugins.exception.PluginHttpException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

public class JenkinsPluginInfoMacro extends BaseMacro {

    private HttpRetrievalService httpRetrievalService;

    private JenkinsRetriever jenkinsRetriever = new JenkinsRetriever();

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
     * @param body in this case null
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

        try {
            JSONObject updateCenter = jenkinsRetriever.retrieveUpdateCenterDetails(httpRetrievalService);
            JSONObject plugins = (JSONObject) updateCenter.get("plugins");

            WikiWriter toBeRendered = null;

            // warnings applicable to this plugin no matter what the release is
            Set<JSONObject> pluginWarnings = new HashSet<JSONObject>();

            // warnings applicable to the current version, which include warnings for all versions even if unpublished
            Set<JSONObject> currentWarnings = new HashSet<JSONObject>();

            JSONArray warnings = (JSONArray) updateCenter.get("warnings");
            if (warnings != null) {
                for (Object w : warnings) {
                    JSONObject warning = (JSONObject) w;
                    try {
                        if (!"plugin".equals(warning.get("type"))) {
                            // not a plugin warning
                            continue;
                        }
                        if (!pluginId.equals(warning.get("name"))) {
                            // not about this plugin
                            continue;
                        }

                        pluginWarnings.add(warning);

                        JSONArray warningVersions = (JSONArray) warning.get("versions");
                        if (warningVersions == null) {
                            warningVersions = new JSONArray();
                        }
                        if (isWarningRelevantForAnyVersion(warningVersions) || isWarningRelevantForSpecificVersion("ifThisMatchesAnythingDoes", warningVersions)) {
                            currentWarnings.add(warning);
                        }
                    } catch (RuntimeException ex) {
                        // ignore -- something wrong on the update site, better to not break the wiki page
                    }
                }
            }

            toBeRendered = new WikiWriter().append("|| Plugin Information ||\n");

            if (plugins.containsKey(pluginId)) {
                JSONObject pluginJSON = (JSONObject) plugins.get(pluginId);

                String title = getString(pluginJSON, "title");

                String version = getString(pluginJSON, "version");

                // filter warnings by whether they apply to the distributed version

                for (JSONObject warning : pluginWarnings) {
                    JSONArray warningVersions = (JSONArray) warning.get("versions");
                    if (warningVersions == null) {
                        warningVersions = new JSONArray();
                    }
                    if (isWarningRelevantForSpecificVersion(version, warningVersions)) {
                        pluginWarnings.add(warning);
                    }
                }
                toBeRendered.append("| View " + title + " [on the plugin site|https://plugins.jenkins.io/" + pluginId + "] for more information. |\n");

            } else {
                if (currentWarnings.isEmpty()) {
                    toBeRendered.append("| No information for the plugin '" + pluginId + "' is available. It may have been removed from distribution. |\n");
                } else {
                    toBeRendered.append("| Distribution of this plugin has been suspended due to unresolved security vulnerabilities, see below. |\n");
                }
            }

            if (!currentWarnings.isEmpty()) {
                // there are warnings
                toBeRendered.append("{warning}The current version of this plugin may not be safe to use. Please review the following warnings before use:\n\n");
                for (JSONObject warning : currentWarnings) {
                    toBeRendered.append(String.format("* [%s|%s]\n", warning.get("message"), warning.get("url")));
                }
                toBeRendered.append("\n{warning}\n\n");
            }

            // remove redundant warnings for plugins with current warnings
            pluginWarnings.removeAll(currentWarnings);

            if (!pluginWarnings.isEmpty()) {
                toBeRendered.append("{info}Older versions of this plugin may not be safe to use. Please review the following warnings before using an older version:\n\n");
                for (JSONObject warning : pluginWarnings) {
                    toBeRendered.append(String.format("* [%s|%s]\n", warning.get("message"), warning.get("url")));
                }
                toBeRendered.append("\n{info}\n\n");
            }

            if (renderContext instanceof PageContext) {
                PageContext pc = (PageContext) renderContext;
                ContentEntityObject entity = pc.getEntity();
                for (Label label : entity.getLabels()) {
                    if ("adopt-this-plugin".equals(label.getName())) {
                        toBeRendered.append("\n\n{note}*This plugin is up for adoption.* ");

                        String message = (String) parameters.get("adopt-message");
                        if (message == null) {
                            toBeRendered.append("Want to help improve this plugin?");
                        } else {
                            toBeRendered.append(message);
                        }

                        toBeRendered.append(" [Click here to learn more|Adopt a Plugin]!{note}");
                    }
                }
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

    private boolean isWarningRelevantForAnyVersion(JSONArray versions) {
        return versions == null || versions.size() == 0;
    }

    private boolean isWarningRelevantForSpecificVersion(String pluginVersion, JSONArray versions) {

        if (isWarningRelevantForAnyVersion(versions)) {
            return false;
        }

        for (Object v : versions) {
            try {
                JSONObject versionEntry = (JSONObject) v;
                String patt = versionEntry.get("pattern").toString();
                Pattern pattern = Pattern.compile(patt);
                if (pattern.matcher(pluginVersion).matches()) {
                    return true;
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return false;
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
