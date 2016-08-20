package com.aprz.observer;

import com.aprz.log.Log;

/**
 * 江苏的客户
 * 
 * @author aprz
 * 
 */
public class JiangsuClient extends Client {
	
	public JiangsuClient(Subject subject) {
		super(subject);
	}

	@Override
	public void update(long bookCount) {
		Log.E("client in jiangsu said -- i get the bookCount, is " + bookCount);
	}

}
