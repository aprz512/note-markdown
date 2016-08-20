package com.aprz.observer;

public abstract class Client implements Observer {
	
	protected Subject mSubject;
	
	public Client(Subject subject) {
		this.mSubject = subject;
		this.mSubject.registerObserver(this);
	}
	
	public void unRegisterSelf() {
		mSubject.unRegisterObserver(this);
	}

}
