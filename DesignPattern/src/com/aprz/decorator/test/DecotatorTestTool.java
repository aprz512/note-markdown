package com.aprz.decorator.test;

import com.aprz.decorator.Bacon;
import com.aprz.decorator.Egg;
import com.aprz.decorator.ZimiJianBing;
import com.aprz.log.Log;

public class DecotatorTestTool {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		/**
		 * 用户要一份紫米的煎饼
		 */
		ZimiJianBing zmJianBing = new ZimiJianBing();

		/**
		 * 加两个鸡蛋 和 一份培根
		 */
		Egg egg1 = new Egg(zmJianBing);
		Egg egg2 = new Egg(egg1);
		Bacon bacon = new Bacon(egg2);
		
		/**
		 * 计算价钱
		 */
		Log.E("jianbing cost " + bacon.cost() + " ￥！");
		
		// 或者可以这样写
		//		 Log.E("jianbing cost " + new Bacon(new Egg(new Egg(zmJianBing))).cost() + " ￥！");
	}

}
