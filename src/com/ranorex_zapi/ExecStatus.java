package com.ranorex_zapi;

/**
 * Created by Sergii on 3/29/17.
 */
public enum ExecStatus {
	SUCCESS("Success"),
	FAILED("Failed");

	private final String description;

	ExecStatus(String description){
		this.description = description;
	}

	@Override
	public String toString(){
		return description;
	}
}
