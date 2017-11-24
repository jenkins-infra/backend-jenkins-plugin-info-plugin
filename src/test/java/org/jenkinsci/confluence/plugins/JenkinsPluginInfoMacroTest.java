package org.jenkinsci.confluence.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

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

import com.atlassian.confluence.util.http.HttpRetrievalService;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.SubRenderer;
import com.atlassian.renderer.v2.macro.MacroException;

@RunWith(MockitoJUnitRunner.class)
public class JenkinsPluginInfoMacroTest {

	@Mock
	private HttpRetrievalService httpRetrievalService = Mockito
			.mock(HttpRetrievalService.class);

	@Mock
	private JenkinsRetriever jenkinsRetriever = Mockito
			.mock(JenkinsRetriever.class);

	@Mock
	private SubRenderer subRenderer = Mockito.mock(SubRenderer.class);

	@InjectMocks
	private JenkinsPluginInfoMacro macro = new JenkinsPluginInfoMacro();

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
	public void validGithub() throws MacroException, IOException,
			PluginHttpException {
		// return the correct stats center details
		String statsString = loadTextFile("cucumber-reports.stats.json");
		Mockito.when(
				jenkinsRetriever.retrieveStatsResponse(
						Mockito.any(HttpRetrievalService.class),
						Mockito.anyString())).thenReturn(statsString);
		macro.setHttpRetrievalService(httpRetrievalService);
		macro.setSubRenderer(subRenderer);
		Map<String, String> inputMap = new HashMap<String, String>();
		inputMap.put("pluginId", "cucumber-reports");
		RenderContext renderContext = new RenderContext();
		String output = macro.execute(inputMap, null, renderContext);
//		String expectedOutput = renderExpectedOutputCucumberReports(true);
//		Assert.assertEquals(expectedOutput, output);
	}

	@Test
	public void validGithubSourceDirAndGitHubUserSet() throws MacroException,
			IOException, PluginHttpException {
		// return the correct stats center details
		String statsString = loadTextFile("cucumber-reports.stats.json");
		Mockito.when(
				jenkinsRetriever.retrieveStatsResponse(
						Mockito.any(HttpRetrievalService.class),
						Mockito.anyString())).thenReturn(statsString);
		macro.setHttpRetrievalService(httpRetrievalService);
		macro.setSubRenderer(subRenderer);
		Map<String, String> inputMap = new HashMap<String, String>();
		inputMap.put("pluginId", "cucumber-reports");
		inputMap.put("githubUser", "masterthought");
		inputMap.put("sourceDir", "jenkins-cucumber-jvm-reports-plugin-java");
		RenderContext renderContext = new RenderContext();
		String output = macro.execute(inputMap, null, renderContext);
//		String expectedOutput = renderExpectedOutputCucumberReportsNew(true);
//		Assert.assertEquals(expectedOutput, output);
	}

	@Test
	public void validGithubWithDependencies() throws MacroException,
			IOException, PluginHttpException {
		// return the correct stats center details
		String statsString = loadTextFile("analysis-collector.stats.json");
		Mockito.when(
				jenkinsRetriever.retrieveStatsResponse(
						Mockito.any(HttpRetrievalService.class),
						Mockito.anyString())).thenReturn(statsString);
		macro.setHttpRetrievalService(httpRetrievalService);
		macro.setSubRenderer(subRenderer);
		Map<String, String> inputMap = new HashMap<String, String>();
		inputMap.put("pluginId", "analysis-collector");
		RenderContext renderContext = new RenderContext();
		String output = macro.execute(inputMap, null, renderContext);
		assertOutputSuccess(output);
	}

	@Test
	public void validNonGithub() throws MacroException, IOException,
			PluginHttpException {
		// return the correct stats center details
		String statsString = loadTextFile("AntepediaReporter-CI-plugin.stats.json");
		Mockito.when(
				jenkinsRetriever.retrieveStatsResponse(
						Mockito.any(HttpRetrievalService.class),
						Mockito.anyString())).thenReturn(statsString);
		macro.setHttpRetrievalService(httpRetrievalService);
		macro.setSubRenderer(subRenderer);
		Map<String, String> inputMap = new HashMap<String, String>();
		inputMap.put("pluginId", "AntepediaReporter-CI-plugin");
		RenderContext renderContext = new RenderContext();
		String output = macro.execute(inputMap, null, renderContext);
		assertOutputSuccess(output);
	}

	@Test
	public void pluginHttpExceptionThrown() throws MacroException, IOException,
			PluginHttpException, ParseException {
		Mockito.when(
				jenkinsRetriever.retrieveUpdateCenterDetails(Mockito
						.any(HttpRetrievalService.class))).thenThrow(
				new PluginHttpException(300));
		macro.setHttpRetrievalService(httpRetrievalService);
		macro.setSubRenderer(subRenderer);
		Map<String, String> inputMap = new HashMap<String, String>();
		inputMap.put("pluginId", "cucumber-reports");
		RenderContext renderContext = new RenderContext();
		String output = macro.execute(inputMap, null, renderContext);
		String expectedOutput = "h4. Plugin Information\n{warning:title=Cannot Load Update Center}\nerror 300 loading update-center.json\n{warning}\n";
		Assert.assertEquals(expectedOutput, output);
	}

	@Test
	public void parseExceptionThrown() throws MacroException, IOException,
			PluginHttpException, ParseException {
		Mockito.when(
				jenkinsRetriever.retrieveUpdateCenterDetails(Mockito
						.any(HttpRetrievalService.class))).thenThrow(
				new ParseException(22341, "ParseException message"));
		macro.setHttpRetrievalService(httpRetrievalService);
		macro.setSubRenderer(subRenderer);
		Map<String, String> inputMap = new HashMap<String, String>();
		inputMap.put("pluginId", "cucumber-reports");
		RenderContext renderContext = new RenderContext();
		String output = macro.execute(inputMap, null, renderContext);
		String expectedOutput = "h4. Plugin Information\n{warning:title=Cannot Load Update Center}\nParseException: Unkown error at position -1.\n{warning}\n";
		Assert.assertEquals(expectedOutput, output);
	}

	@Test
	public void ioExceptionThrown() throws MacroException, IOException,
			PluginHttpException, ParseException {
		Mockito.when(
				jenkinsRetriever.retrieveUpdateCenterDetails(Mockito
						.any(HttpRetrievalService.class))).thenThrow(
				new IOException("IOException message"));
		macro.setHttpRetrievalService(httpRetrievalService);
		macro.setSubRenderer(subRenderer);
		Map<String, String> inputMap = new HashMap<String, String>();
		inputMap.put("pluginId", "cucumber-reports");
		RenderContext renderContext = new RenderContext();
		String output = macro.execute(inputMap, null, renderContext);
		String expectedOutput = "h4. Plugin Information\n{warning:title=Cannot Load Update Center}\nIOException: IOException message\n{warning}\n";
		Assert.assertEquals(expectedOutput, output);
	}

	@Test
	public void getStatsParserPluginHttpExceptionThrown()
			throws MacroException, IOException, PluginHttpException {
		Mockito.when(
				jenkinsRetriever.retrieveStatsResponse(
						Mockito.any(HttpRetrievalService.class),
						Mockito.anyString())).thenThrow(
				new PluginHttpException(300));
		macro.setHttpRetrievalService(httpRetrievalService);
		macro.setSubRenderer(subRenderer);
		Map<String, String> inputMap = new HashMap<String, String>();
		inputMap.put("pluginId", "cucumber-reports");
		RenderContext renderContext = new RenderContext();
		String output = macro.execute(inputMap, null, renderContext);
		assertOutputSuccess(output);
	}

	private void assertOutputSuccess(String content) {
		System.out.println("output: " + content);
		Assert.assertFalse(content.contains("{warning}"));
	}

	@Test
	public void getStatsParserOtherExceptionThrown() throws MacroException,
			IOException, PluginHttpException {
		Mockito.when(
				jenkinsRetriever.retrieveStatsResponse(
						Mockito.any(HttpRetrievalService.class),
						Mockito.anyString())).thenThrow(
				new NullPointerException("npe"));
		macro.setHttpRetrievalService(httpRetrievalService);
		macro.setSubRenderer(subRenderer);
		Map<String, String> inputMap = new HashMap<String, String>();
		inputMap.put("pluginId", "cucumber-reports");
		RenderContext renderContext = new RenderContext();
		String output = macro.execute(inputMap, null, renderContext);
		assertOutputSuccess(output);
	}

	@Test
	public void missingPlugin() throws MacroException {
		Map<String, String> inputMap = new HashMap<String, String>();
		inputMap.put("pluginId", "does-not-exist");
		RenderContext renderContext = new RenderContext();
		String output = macro.execute(inputMap, null, renderContext);
		String expectedOutput = "|| No information for the plugin 'does-not-exist' is available. It may have been removed from distribution. ||\n";
		Assert.assertEquals(expectedOutput, output);
	}

	@Test
	public void noPluginSpecified() throws MacroException {
		JenkinsPluginInfoMacro macro = new JenkinsPluginInfoMacro();
		Map<String, String> inputMap = new HashMap<String, String>();
		RenderContext renderContext = new RenderContext();
		String output = macro.execute(inputMap, null, renderContext);
		Assert.assertEquals("No plugin specified.", output);
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
		Assert.assertEquals(RenderMode.NO_RENDER, macro.getBodyRenderMode());
	}

	private String loadTextFile(String fileName)
			throws UnsupportedEncodingException {
		final InputStream stream = JenkinsPluginInfoMacroTest.class
				.getResourceAsStream("/" + fileName);
		Reader reader = new InputStreamReader(stream, "UTF-8");
		String json = null;
		try {
			json = IOUtils.toString(reader);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return json;
	}
}
