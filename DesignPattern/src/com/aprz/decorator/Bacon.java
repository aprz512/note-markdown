package com.aprz.decorator;

public class Bacon extends Ingredients {

	public Bacon(JianBing jianBing) {
		super(jianBing);
	}

	@Override
	public float getIngredientsPrice() {
		return IngredientsPrice.BACON_PRICE;
	}

}
