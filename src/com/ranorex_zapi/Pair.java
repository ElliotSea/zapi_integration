package com.ranorex_zapi;

/**
 * Created by Sergii on 3/29/17.
 */
public class Pair {
	private String a;
	private String b;

	Pair(String a, String b){
		this.a = a;
		this.b = b;
	}

	public String a(){
		return this.a;
	}

	public String b(){
		return this.b;
	}
}
