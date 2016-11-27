package com.aprz.factory;

/**
 * 披萨店里有很多种披萨，我们需要根据客人的需要来制作不同的披萨
 * 
 * 当店铺的规格越来越大的时候，我们可以在全国各地开分店，但是这里有个问题， 不同的地方，有不同的口味，这如何是好？
 * 
 * 还有你如何控制每个分店的披萨制作过程？
 * 
 * @author aprz
 * 
 */
public abstract class PizzaStore {

	public Pizza dealOrder(String pizzaName) {
		Pizza pizza = createPizza(pizzaName);
		pizza.prepare();
		pizza.bake();
		pizza.cut();
		pizza.box();
		return pizza;
	}

	public abstract Pizza createPizza(String pizzaName);

}
