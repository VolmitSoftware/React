package com.volmit.react.api;

import java.io.File;

public class StackTraceRecordBook extends RecordBook<StackTraceRecord>
{
	public StackTraceRecordBook(String type, File recordFile)
	{
		super(type, recordFile);
	}

	@Override
	public StackTraceRecord createDummyRecord(long time, String type)
	{
		return new StackTraceRecord(time, null, type);
	}
}
