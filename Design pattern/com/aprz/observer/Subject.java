package com.aprz.observer;

public interface Subject {
	
	void registerObserver(Observer observer);
	
	void unRegisterObserver(Observer observer);
	
	void notifyObservers();

}
