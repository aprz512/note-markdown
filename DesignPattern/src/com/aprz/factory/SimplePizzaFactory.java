package com.aprz.factory;

public class SimplePizzaFactory {

	/**
	 * 这里就是一个简单工厂模式， 严格来说，这算不上是一个模式，更像是一种编程习惯。
	 * 
	 * @param pizzaName
	 *            披萨名
	 * @return 披萨对象
	 */
	public Pizza createPizza(String pizzaName) {
		if (pizzaName.equals("cheese")) {
			return new CheesePizza();
		} else if (pizzaName.equals("clam")) {
			return new ClamPizza();
		}
		return null;
	}

}
