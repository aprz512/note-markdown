package com.aprz.observer;

public interface Observer {
	/**
	 * 新华出版社书的数量发生变化的时候，该方法会被触发
	 * @param bookCount 书的数量
	 */
	void update(long bookCount);
}
