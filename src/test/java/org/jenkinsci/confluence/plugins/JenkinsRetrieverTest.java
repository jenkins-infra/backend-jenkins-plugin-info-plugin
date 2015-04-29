package org.jenkinsci.confluence.plugins;

import java.io.IOException;
import java.io.InputStream;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;
import org.jenkinsci.confluence.plugins.exception.PluginHttpException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.atlassian.confluence.util.http.HttpResponse;
import com.atlassian.confluence.util.http.HttpRetrievalService;

@RunWith(PowerMockRunner.class)
@PrepareForTest(IOUtils.class)
public class JenkinsRetrieverTest {

	@Mock
	private HttpRetrievalService httpRetrievalService = PowerMockito
			.mock(HttpRetrievalService.class);

	private JenkinsRetriever jenkinsRetriever = new JenkinsRetriever();

	@Test
	public void testRetrieveUpdateCenterDetailsSuccess() throws IOException,
			PluginHttpException, ParseException {
		String returnJson = "{" + "\"employees\": [" + "{"
				+ "\"firstName\": \"Peter\"," + "\"lastName\": \"Jones\"" + "}"
				+ "]}";
		PowerMockito.mockStatic(IOUtils.class);
		HttpResponse response = Mockito.mock(HttpResponse.class);
		Mockito.when(response.getStatusCode()).thenReturn(200);
		Mockito.when(httpRetrievalService.get(Mockito.anyString())).thenReturn(
				response);
		Mockito.when(IOUtils.toString(Mockito.any(InputStream.class)))
				.thenReturn(returnJson);
		JSONObject returned = jenkinsRetriever
				.retrieveUpdateCenterDetails(httpRetrievalService);
		Assert.assertEquals(1, returned.size());
		Assert.assertEquals(1, ((JSONArray) returned.get("employees")).size());
	}

	@Test
	public void testRetrieveUpdateCenterDetailsTrim() throws IOException,
			PluginHttpException, ParseException {
		String returnJson = "updateCenter.post(" + "{" + "\"employees\": ["
				+ "{" + "\"firstName\": \"Peter\"," + "\"lastName\": \"Jones\""
				+ "}" + "]}" + ");";
		PowerMockito.mockStatic(IOUtils.class);
		HttpResponse response = Mockito.mock(HttpResponse.class);
		Mockito.when(response.getStatusCode()).thenReturn(200);
		Mockito.when(httpRetrievalService.get(Mockito.anyString())).thenReturn(
				response);
		Mockito.when(IOUtils.toString(Mockito.any(InputStream.class)))
				.thenReturn(returnJson);
		JSONObject returned = jenkinsRetriever
				.retrieveUpdateCenterDetails(httpRetrievalService);
		Assert.assertEquals(1, returned.size());
		Assert.assertEquals(1, ((JSONArray) returned.get("employees")).size());
	}

	@Test(expected = PluginHttpException.class)
	public void testRetrieveUpdateCenterDetailsPluginHttpException()
			throws IOException, PluginHttpException, ParseException {
		HttpResponse response = Mockito.mock(HttpResponse.class);
		Mockito.when(response.getStatusCode()).thenReturn(300);
		Mockito.when(httpRetrievalService.get(Mockito.anyString())).thenReturn(
				response);
		jenkinsRetriever.retrieveUpdateCenterDetails(httpRetrievalService);
	}

	@Test(expected = IOException.class)
	public void testRetrieveUpdateCenterDetailsIOException()
			throws IOException, PluginHttpException, ParseException {
		HttpResponse response = Mockito.mock(HttpResponse.class);
		Mockito.when(response.getStatusCode()).thenReturn(300);
		Mockito.when(httpRetrievalService.get(Mockito.anyString())).thenThrow(
				new IOException());
		jenkinsRetriever.retrieveUpdateCenterDetails(httpRetrievalService);
	}

	@Test
	public void testRetrieveStatsResponseSuccess() throws IOException,
			PluginHttpException {
		PowerMockito.mockStatic(IOUtils.class);
		HttpResponse response = Mockito.mock(HttpResponse.class);
		Mockito.when(response.getStatusCode()).thenReturn(200);
		Mockito.when(httpRetrievalService.get(Mockito.anyString())).thenReturn(
				response);
		Mockito.when(IOUtils.toString(Mockito.any(InputStream.class)))
				.thenReturn("fred      ");
		Assert.assertEquals("fred", jenkinsRetriever.retrieveStatsResponse(
				httpRetrievalService, "dummy"));
	}

	@Test(expected = PluginHttpException.class)
	public void testRetrieveStatsResponsePluginHttpException()
			throws IOException, PluginHttpException {
		HttpResponse response = Mockito.mock(HttpResponse.class);
		Mockito.when(response.getStatusCode()).thenReturn(300);
		Mockito.when(httpRetrievalService.get(Mockito.anyString())).thenReturn(
				response);
		jenkinsRetriever.retrieveStatsResponse(httpRetrievalService, "dummy");
	}

	@Test(expected = IOException.class)
	public void testRetrieveStatsResponseIOException() throws IOException,
			PluginHttpException {
		Mockito.when(httpRetrievalService.get(Mockito.anyString())).thenThrow(
				new IOException());
		jenkinsRetriever.retrieveStatsResponse(httpRetrievalService, "dummy");
	}

}
