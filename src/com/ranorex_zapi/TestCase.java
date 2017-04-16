package com.ranorex_zapi;

/**
 * Created by Sergii on 3/23/17 for CareCloud
 */
public class TestCase {
	private String automationName;
	private String jiraKey;
	private String jiraId;

	TestCase(String automationName, String jiraKey, String jiraId){
		this.automationName = automationName;
		this.jiraKey = jiraKey;
		this.jiraId = jiraId;
	}

	public String getAutomationName() {
		return automationName;
	}

	public String getJiraKey() {
		return jiraKey;
	}

	public String getJiraId() {
		return jiraId;
	}

}
