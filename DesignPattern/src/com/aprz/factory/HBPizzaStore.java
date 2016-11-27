package com.aprz.factory;

/**
 * hb 人喜欢吃普通味道的披萨
 * 
 * @author aprz
 * 
 */
public class HBPizzaStore extends PizzaStore {

	@Override
	public Pizza createPizza(String pizzaName) {
		HBPizzaFactory factory = new HBPizzaFactory();
		return factory.createPizza(pizzaName);
	}

}
