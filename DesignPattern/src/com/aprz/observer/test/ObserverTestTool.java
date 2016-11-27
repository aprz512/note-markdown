package com.aprz.observer.test;

import com.aprz.observer.BeijingClient;
import com.aprz.observer.JiangsuClient;
import com.aprz.observer.XinhuaPublishing;

/**
 * 观察者模式测试工具
 * 
 * @author aprz
 * 
 */
public class ObserverTestTool {

	public static void main(String[] args) {
		XinhuaPublishing publishing = new XinhuaPublishing();

		/**
		 * 订阅者需要主动向出版社发出订阅请求
		 * 订阅请求封装在了构造函数中
		 */
		new BeijingClient(publishing);
		new JiangsuClient(publishing);

		/**
		 * 当出版社书籍数量变化的时候，订阅者会收到信息（查看log）
		 */
		publishing.addOneBook();
		publishing.delOneBook();
	}

}
