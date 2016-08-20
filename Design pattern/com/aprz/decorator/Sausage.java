package com.aprz.decorator;

/**
 * 烤肠配料
 * 
 * @author aprz
 * 
 *         1 份 1块5
 */
public class Sausage extends Ingredients {

	public Sausage(JianBing jianBing) {
		super(jianBing);
	}

	@Override
	public float getIngredientsPrice() {
		return IngredientsPrice.SAUSAGE_PRICE + mJianBing.cost();
	}

}
