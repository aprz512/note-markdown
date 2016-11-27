package com.aprz.strategy;

import com.aprz.log.Log;

public class FlyWithWings implements FlyBehavior {

	@Override
	public void fly() {
		Log.E("I am flying with wings");
	}

}
