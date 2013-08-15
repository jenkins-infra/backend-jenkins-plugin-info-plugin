package org.jenkinsci.confluence.plugins.exception;

public class PluginHttpException extends Exception {

	private static final long serialVersionUID = -6730092381542990629L;

	private int statusCode;

	public PluginHttpException(int statusCode) {
		this.statusCode = statusCode;
	}

	public int getStatusCode() {
		return statusCode;
	}

}
