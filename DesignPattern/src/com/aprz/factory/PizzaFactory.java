package com.aprz.factory;

/**
 * 看起来仅仅是将简单工厂的方法抽象了出来而已
 * 
 * @author aprz
 * 
 */
public abstract class PizzaFactory {

	public abstract Pizza createPizza(String pizzaName);

}
