package com.aprz.strategy;

import com.aprz.log.Log;

public class FlyNoWay implements FlyBehavior {

	@Override
	public void fly() {
		Log.E("I can not fly.");
	}

}
