package com.aprz.observer;

import com.aprz.log.Log;

/**
 * 新华出版社书籍数量管理工具
 * 
 * @author aprz
 * 
 */
public class XinhuaBookCountManager {
	private long mBookCount;

	protected void addOneBook() {
		++mBookCount;

		Log.E("add one book");
	}

	protected void delOneBook() {
		--mBookCount;

		Log.E("del one book");
	}

	protected long getBookCount() {
		return mBookCount;
	}
}
