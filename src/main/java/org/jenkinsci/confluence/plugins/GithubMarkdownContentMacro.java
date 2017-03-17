package org.jenkinsci.confluence.plugins;

import com.atlassian.confluence.util.http.HttpRetrievalService;
import com.atlassian.plugin.Plugin;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.SubRenderer;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.renderer.v2.macro.MacroException;
import org.jenkinsci.confluence.plugins.exception.PluginHttpException;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.pegdown.Extensions;
import org.pegdown.LinkRenderer;
import org.pegdown.PegDownProcessor;
import org.pegdown.ast.RootNode;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * Created by acearl on 4/23/2016.
 */
public class GithubMarkdownContentMacro extends BaseMacro {
    HttpRetrievalService httpRetrievalService;

    private JenkinsRetriever jenkinsRetriever = new JenkinsRetriever();

    /**
     * Setter method for automatic injection of the {@link HttpRetrievalService}.
     *
     * @param httpRetrievalService the http retrieval service to use
     */
    public void setHttpRetrievalService(HttpRetrievalService httpRetrievalService) {
        this.httpRetrievalService = httpRetrievalService;
    }

    public boolean isInline() {
        return false;
    }

    public boolean hasBody() {
        return false;
    }

    public RenderMode getBodyRenderMode() {
        return RenderMode.NO_RENDER;
    }

    private String getString(JSONObject o, String prop) {
        if(o.containsKey(prop))
            return o.get(prop).toString();
        else
            return "n/a";
    }

    public String execute(Map parameters, String s, RenderContext renderContext) throws MacroException {
        String pluginId = (String) parameters.get("pluginId");
        if (pluginId == null) {
            pluginId = (String) parameters.get("0"); // Accept pluginId value without "pluginId="
        }
        if (pluginId == null) {
            return "No plugin specified.";
        }

        String sourceDir = (String) parameters.get("sourceDir");
        String fileName = (String) parameters.get("fileName");
        if (fileName == null) {
            fileName = "README.md";
        }
        StringBuilder toBeRendered = new StringBuilder();

        try {
            JSONObject updateCenter = jenkinsRetriever.retrieveUpdateCenterDetails(httpRetrievalService);
            JSONObject plugins = (JSONObject) updateCenter.get("plugins");

            if (plugins.containsKey(pluginId)) {
                JSONObject pluginJSON = (JSONObject) plugins.get(pluginId);

                String name = getString(pluginJSON, "name");

                boolean isGithub = getString(pluginJSON, "scm").endsWith("github.com"); // Default to svn
                if (!isGithub) {
                    return "Plugin source not on Github";
                }
                if (sourceDir == null) {
                    sourceDir = name + (isGithub && !name.endsWith("-plugin") ? "-plugin" : "");
                }

                String githubBaseUrl = "https://raw.githubusercontent.com/jenkinsci/" + sourceDir + "/master/";
                try {
                    String content = jenkinsRetriever.retrieveFile(httpRetrievalService, githubBaseUrl + fileName);
                    if (content != null) {
                        if (fileName.endsWith(".txt") || "README".equals(fileName)) {
                            toBeRendered.append(content);
                        } else {
                            PegDownProcessor processor = new PegDownProcessor(Extensions.FENCED_CODE_BLOCKS | Extensions.HARDWRAPS, 20000);
                            RootNode root = processor.parseMarkdown(content.toCharArray());
                            toBeRendered.append(new ToConfluenceSerializer(new LinkRenderer(), githubBaseUrl).toConfluence(root));
                        }
                    } else {
                        toBeRendered.append("h4. Github Markdown Content\n"
                                + "{warning:title=Cannot load Github Markdown Content}\n"
                                + "Could not find file named " + fileName + "\n"
                                + "{warning}\n");
                    }
                } catch (PluginHttpException e) {

                } catch (URISyntaxException e) {

                }
            }

            return subRenderer.render(toBeRendered.toString(), renderContext);
        } catch (IOException e) {
            return subRenderer.render("h4. Github Markdown Content\n"
                    + "{warning:title=Cannot load Github Markdown Content}\n"
                    + "IOException: " + e.getMessage() + "\n"
                    + "{warning}\n", renderContext);
        } catch (PluginHttpException e) {
            return subRenderer.render("h4. Github Markdown Content\n"
                    + "{warning:title=Cannot Load Github Markdown Content}\n" + "error "
                    + e.getStatusCode() + " loading " + fileName + "\n"
                    + "{warning}\n", renderContext);
        } catch (ParseException e) {
            return subRenderer.render("h4. Github Markdown Content\n"
                    + "{warning:title=Cannot Load Github Markdown Content}\n"
                    + "ParseException: " + e.getMessage() + "\n"
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
