package com.aprz.strategy;

import com.aprz.log.Log;

public class YellowHeadDuck extends Duck {
	
	public YellowHeadDuck () {
		flyBehavior = new FlyWithWings();
	}

	@Override
	public void swim() {
		Log.E("yellow----I am swimming.");
	}

	@Override
	public void quark() {
		Log.E("yellow----quark......");
	}

	@Override
	public void display() {
		// TODO Auto-generated method stub

	}

}
