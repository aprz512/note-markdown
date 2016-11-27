package com.aprz.strategy;

import com.aprz.log.Log;

public class RedHeadDuck extends Duck {
	
	public RedHeadDuck() {
		flyBehavior = new FlyWithWings();
	}

	@Override
	public void swim() {
		Log.E("red----I am swimming.");
	}

	@Override
	public void quark() {
		Log.E("red-----quark......");
	}

	@Override
	public void display() {
		// TODO Auto-generated method stub

	}

}
