package com.aprz.strategy;

import com.aprz.log.Log;

/**
 * 玩具鸭子
 * @author aprz
 *
 */
public class ToyDuck extends Duck {
	
	public ToyDuck() {
		flyBehavior = new FlyNoWay();
	}

	@Override
	public void swim() {
		Log.E("toy-----I am swimming.");
	}

	@Override
	public void quark() {
		Log.E("toy----quark......");
	}

	@Override
	public void display() {
		// TODO Auto-generated method stub

	}

}
