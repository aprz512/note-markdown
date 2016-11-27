package com.aprz.strategy;

import com.aprz.log.Log;

/**
 * 
 * @author aprz 
 * 
 * 现在有不同的鸭子，红头，黄头等等鸭子，这些都是现实生活中存在的。
 * 但是除了这些，还有玩具鸭子，玩具鸭子也是具有，呱呱叫，游泳，展示自己的形象这3种形象。
 * 现在问题来了，如果我们要加一个让鸭子飞的方法，那该怎么做？提示：玩具鸭子不会飞。
 * 如果我有几千种鸭子，又该怎么办？
 * 
 * 试试把鸭子外观展示，也用策略模式来编写。
 */
public abstract class Duck {
	
	protected FlyBehavior flyBehavior;

	/**
	 * 鸭子会游泳
	 */
	public abstract void swim();

	/**
	 * 鸭子会呱呱叫
	 */
	public abstract void quark();

	/**
	 * 展示鸭子的形象
	 */
	public abstract void display();

	/**
	 * 鸭子怎么飞
	 */
	public void performFly() {
		Log.E(this.getClass().getSimpleName() + ":");
		flyBehavior.fly();
	}
	
	public void setFlyBehavior(FlyBehavior flyBehavior) {
		this.flyBehavior = flyBehavior; 
	}
}
