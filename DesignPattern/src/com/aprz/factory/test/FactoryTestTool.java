package com.aprz.factory.test;

import com.aprz.factory.BJPizzaStore;
import com.aprz.factory.HBPizzaStore;
import com.aprz.factory.Pizza;
import com.aprz.log.Log;

public class FactoryTestTool {
	
	public static void main(String[] args) {
		// 建立 BJ 披萨点
		BJPizzaStore bjStore = new BJPizzaStore();
		Pizza bjPizza = bjStore.dealOrder("clam");
		Log.E(bjPizza.getClass().getSimpleName());
		
		// 建立 HB 披萨点
		HBPizzaStore hbStore = new HBPizzaStore();
		Pizza hbPizza = hbStore.dealOrder("clam");
		Log.E(hbPizza.getClass().getSimpleName());
	}

}
