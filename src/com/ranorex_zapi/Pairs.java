package com.ranorex_zapi;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Sergii on 3/29/17.
 */
public class Pairs {
	private List<Pair> pairList = new ArrayList<Pair>();

	Pairs(Pair... pairs){
		for (Pair pair : pairs){
			pairList.add(pair);
		}
	}

	public List<Pair> get(){
		return this.pairList;
	}


}
