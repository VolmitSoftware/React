package com.volmit.react.api;

import com.volmit.react.util.JSONObject;

public abstract class Record<T> implements IRecord<T>
{
	private String recordType;
	private long recordTime;
	protected T object;

	public Record(long recordTime, String recordType)
	{
		this.recordTime = recordTime;
		this.recordType = recordType;
	}

	public Record(long recordTime, T object, String recordType)
	{
		this(recordTime, recordType);
		this.object = object;
	}

	@Override
	public String getRecordType()
	{
		return recordType;
	}

	@Override
	public long getRecordTime()
	{
		return recordTime;
	}

	@Override
	public T getRecordObject()
	{
		return object;
	}

	@Override
	public abstract JSONObject toJSON();

	@Override
	public abstract void fromJSON(JSONObject k);
}
