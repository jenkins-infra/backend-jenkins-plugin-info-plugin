package org.jenkinsci.confluence.plugins;

import java.io.IOException;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.jenkinsci.confluence.plugins.exception.PluginHttpException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.atlassian.confluence.util.http.HttpResponse;
import com.atlassian.confluence.util.http.HttpRetrievalService;

public class JenkinsRetriever {

	public JSONObject retrieveUpdateCenterDetails(
			HttpRetrievalService httpRetrievalService) throws IOException,
			PluginHttpException, ParseException {
		HttpResponse response = httpRetrievalService
				.get("http://updates.jenkins-ci.org/update-center.json");
		if (response.getStatusCode() != 200) {
			throw new PluginHttpException(response.getStatusCode());
		}
		String rawUpdateCenter = IOUtils.toString(response.getResponse())
				.trim();
		if (rawUpdateCenter.startsWith("updateCenter.post(")) {
			rawUpdateCenter = rawUpdateCenter.substring("updateCenter.post("
					.length());
		}
		if (rawUpdateCenter.endsWith(");")) {
			rawUpdateCenter = rawUpdateCenter.substring(0,
					rawUpdateCenter.lastIndexOf(");"));
		}
		JSONParser parser = new JSONParser();
		JSONObject updateCenter = (JSONObject) parser.parse(rawUpdateCenter);
		return updateCenter;
	}

	public String retrieveStatsResponse(
			HttpRetrievalService httpRetrievalService, String pluginId)
			throws IOException, PluginHttpException {
		HttpResponse statsResponse = httpRetrievalService
				.get("http://stats.jenkins-ci.org/plugin-installation-trend/"
						+ pluginId + ".stats.json");
		if (statsResponse.getStatusCode() != 200) {
			throw new PluginHttpException(statsResponse.getStatusCode());
		}
		String rawStats = IOUtils.toString(statsResponse.getResponse()).trim();
		return rawStats;
	}

	public String retrieveFile(
			HttpRetrievalService httpRetrievalService,
			String url)
		throws IOException, PluginHttpException {

		HttpResponse response = httpRetrievalService.get(url);
		if(response.getStatusCode() != 200) {
			throw new PluginHttpException(response.getStatusCode());
		}

		return IOUtils.toString(response.getResponse());
	}
}
