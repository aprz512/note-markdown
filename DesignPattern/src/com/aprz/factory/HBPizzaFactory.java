package com.aprz.factory;

public class HBPizzaFactory extends PizzaFactory {

	@Override
	public Pizza createPizza(String pizzaName) {
		if (pizzaName.equals("cheese")) {
			return new CheesePizza();
		} else if (pizzaName.equals("clam")) {
			return new ClamPizza();
		}
		return null;
	}

}
