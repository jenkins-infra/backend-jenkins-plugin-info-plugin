package org.jenkinsci.confluence.plugins;

import com.atlassian.confluence.util.http.HttpRetrievalService;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.SubRenderer;
import com.atlassian.renderer.v2.macro.MacroException;
import junit.framework.Assert;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.confluence.plugins.exception.PluginHttpException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;

/**
 * Created by acearl on 4/24/2016.
 */
@RunWith(MockitoJUnitRunner.class)
public class GithubReadmeContentMacroTest {
    @Mock
    private HttpRetrievalService httpRetrievalService = Mockito
            .mock(HttpRetrievalService.class);

    @Mock
    private JenkinsRetriever jenkinsRetriever = Mockito
            .mock(JenkinsRetriever.class);

    @Mock
    private SubRenderer subRenderer = Mockito.mock(SubRenderer.class);

    @InjectMocks
    private GithubMarkdownContentMacro macro = new GithubMarkdownContentMacro();

    @Before
    public void buildUp() throws IOException, PluginHttpException,
            ParseException {
        // return the correct update center details
        String jsonString = loadTextFile("update-center.json");
        JSONParser parser = new JSONParser();
        JSONObject updateCenter = (JSONObject) parser.parse(jsonString);
        Mockito.when(
                jenkinsRetriever.retrieveUpdateCenterDetails(Mockito
                        .any(HttpRetrievalService.class))).thenReturn(
                updateCenter);
        // ensure the string to be rendered is returned as-is to the test
        Mockito.when(
                subRenderer.render(Mockito.anyString(),
                        Mockito.any(RenderContext.class))).thenAnswer(
                new Answer<String>() {
                    // @Override
                    public String answer(InvocationOnMock invocation)
                            throws Throwable {
                        Object[] args = invocation.getArguments();
                        return (String) args[0];
                    }
                });
    }

    @Test
    public void testRenderPlainText() throws IOException, PluginHttpException, MacroException {
        String fileContent = loadTextFile("testRenderPlainText.txt");
        Mockito.when(
                jenkinsRetriever.retrieveFile(Mockito.any(HttpRetrievalService.class), Mockito.endsWith("README"))).thenReturn(
                fileContent);

        macro.setHttpRetrievalService(httpRetrievalService);
        macro.setSubRenderer(subRenderer);
        Map<String, String> inputMap = new HashMap<String, String>();
        inputMap.put("pluginId", "email-ext");
        inputMap.put("fileName", "README");
        RenderContext renderContext = new RenderContext();
        String output = macro.execute(inputMap, null, renderContext);
        assertEquals(fileContent, output);
    }

    @Test
    public void testRenderMarkdown() throws IOException, PluginHttpException, MacroException {
        String fileContent = loadTextFile("testRenderMarkdown.md");
        Mockito.when(
                jenkinsRetriever.retrieveFile(Mockito.any(HttpRetrievalService.class), Mockito.endsWith("README.md"))).thenReturn(
                fileContent);

        macro.setHttpRetrievalService(httpRetrievalService);
        macro.setSubRenderer(subRenderer);
        Map<String, String> inputMap = new HashMap<String, String>();
        inputMap.put("pluginId", "email-ext");
        RenderContext renderContext = new RenderContext();
        String output = macro.execute(inputMap, null, renderContext);

        String expected = loadTextFile("testRenderMarkdown.out");
        assertEquals(expected.trim(), output.trim());
    }

    @Test
    public void testIsInline() {
        Assert.assertFalse(macro.isInline());
    }

    @Test
    public void testHasBody() {
        Assert.assertFalse(macro.hasBody());
    }

    @Test
    public void testGetBodyRenderMode() {
        assertEquals(RenderMode.NO_RENDER, macro.getBodyRenderMode());
    }

    private String loadTextFile(String fileName)
            throws UnsupportedEncodingException {
        final InputStream stream = GithubReadmeContentMacroTest.class
                .getResourceAsStream("/" + fileName);
        Reader reader = new InputStreamReader(stream, "UTF-8");
        String content = null;
        try {
            content = IOUtils.toString(reader);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return content;
    }
}
