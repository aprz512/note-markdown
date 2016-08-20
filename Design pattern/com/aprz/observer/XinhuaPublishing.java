package com.aprz.observer;

import java.util.ArrayList;

/**
 * 新华出版社
 * @author aprz
 *
 */
public class XinhuaPublishing implements Subject {
	
	private ArrayList<Observer> mObservers = new ArrayList<Observer> ();
	
	private XinhuaBookCountManager mBookManager = new XinhuaBookCountManager();
	
	public void addOneBook() {
		mBookManager.addOneBook();
		notifyObservers();
	}
	
	public void delOneBook() {
		mBookManager.delOneBook();
		notifyObservers();
	}

	public void registerObserver(Observer observer) {
		mObservers.add(observer);
	}

	public void unRegisterObserver(Observer observer) {
		mObservers.remove(observer);
	}

	public void notifyObservers() {
		for(Observer observer : mObservers) {
			observer.update(mBookManager.getBookCount());
		}
	}

}
