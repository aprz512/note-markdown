package com.aprz.decorator;

/**
 * 鸡蛋配料
 * 
 * @author aprz
 * 
 *         1 份 1 块
 */
public class Egg extends Ingredients {

	public Egg(JianBing jianBing) {
		super(jianBing);
	}

	@Override
	public float getIngredientsPrice() {
		return IngredientsPrice.EGG_PRICE;
	}

}
