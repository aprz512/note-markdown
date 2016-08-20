package com.aprz.observer;

import com.aprz.log.Log;

/**
 * 北京的客户
 * 
 * @author aprz
 * 
 */
public class BeijingClient extends Client {

	public BeijingClient(Subject subject) {
		super(subject);
	}

	@Override
	public void update(long bookCount) {
		Log.E("client in beijing said -- i get the bookCount, is " + bookCount);
	}

}
