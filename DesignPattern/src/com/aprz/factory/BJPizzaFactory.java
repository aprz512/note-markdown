package com.aprz.factory;

public class BJPizzaFactory extends PizzaFactory {

	@Override
	public Pizza createPizza(String pizzaName) {
		if (pizzaName.equals("cheese")) {
			return new BJCheesePizza();
		} else if (pizzaName.equals("clam")) {
			return new BJClamPizza();
		}
		
		return null;
	}

}
