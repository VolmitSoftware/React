package com.volmit.react.api;

import java.io.File;

import com.volmit.volume.lang.collections.GMap;

public interface IRecordBook<T extends IRecord<?>>
{
	public int getSize();

	public T getRecord(long record);

	public long getOldestRecordTime();

	public void addRecord(T t);

	public long getLatestRecordTime();

	public int countRecords(long from, long to);

	public GMap<Long, T> getRecords(long from, long to);

	public int purgeRecordsBefore(long time);

	public void save();

	public File getFile();
}
