package com.aprz.decorator;

/**
 * 不想每个配料类都写一份 
 * protected JianBing mJianBing;
 * public Ingredients(JianBing jianBing) { this.mJianBing = jianBing; }
 * 所以就写了这样一个类
 * 
 * @author aprz
 * 
 */
public abstract class Ingredients extends JianBing {

	protected JianBing mJianBing;

	public Ingredients(JianBing jianBing) {
		this.mJianBing = jianBing;
	}

	@Override
	public final float cost() {
		return getIngredientsPrice() + mJianBing.cost();
	}

	public abstract float getIngredientsPrice();

}
