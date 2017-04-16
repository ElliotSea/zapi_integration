package com.ranorex_zapi;

/**
 * Created by Sergii on 3/23/17 for CareCloud
 */
public class TestExecution {
	private String automationName;
	private String status;

	TestExecution(String automationName, String status){
		this.automationName = automationName;
		this.status = status;
	}

	public String getAutomationName() {
		return automationName;
	}

	public String getStatus() {
		return status;
	}
}
