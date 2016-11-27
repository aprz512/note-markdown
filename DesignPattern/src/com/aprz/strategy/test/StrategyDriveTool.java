package com.aprz.strategy.test;

import com.aprz.strategy.Duck;
import com.aprz.strategy.FlyNoWay;
import com.aprz.strategy.RedHeadDuck;
import com.aprz.strategy.ToyDuck;
import com.aprz.strategy.YellowHeadDuck;

public class StrategyDriveTool {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Duck red = new RedHeadDuck();
		Duck yellow = new YellowHeadDuck();
		Duck toy = new ToyDuck();
		
		red.quark();
		red.swim();
		red.performFly();
		
		yellow.quark();
		yellow.swim();
		yellow.performFly();
		
		toy.quark();
		toy.swim();
		toy.performFly();
		
		// red 受伤了
		red.setFlyBehavior(new FlyNoWay());
		red.performFly();
	}

}
