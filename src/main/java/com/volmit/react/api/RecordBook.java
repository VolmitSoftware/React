package com.volmit.react.api;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import com.volmit.react.util.Ex;
import com.volmit.react.util.JSONObject;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;

public abstract class RecordBook<T extends IRecord<?>> implements IRecordBook<T>
{
	private File recordFile;
	private JSONObject js;
	private String type;

	public RecordBook(String type, File recordFile)
	{
		this.recordFile = recordFile;
		this.type = type;
		js = new JSONObject();

		if(!recordFile.exists())
		{
			try
			{
				write();
			}

			catch(Throwable e)
			{
				Ex.t(e);
			}
		}

		try
		{
			read();
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	@Override
	public void save()
	{
		try
		{
			write();
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	@Override
	public int getSize()
	{
		return js.keySet().size();
	}

	@Override
	public void addRecord(T t)
	{
		js.put(t.getRecordTime() + "", t.toJSON());
	}

	@Override
	public T getRecord(long record)
	{
		if(js.has("" + record))
		{
			T t = createDummyRecord(record, type);
			JSONObject jsx = js.getJSONObject("" + record);
			t.fromJSON(jsx);
			return t;
		}

		return null;
	}

	public abstract T createDummyRecord(long time, String type);

	@Override
	public long getOldestRecordTime()
	{
		long beginningOfTime = Long.MAX_VALUE;

		for(String i : js.keySet())
		{
			long k = Long.valueOf(i);

			if(k < beginningOfTime)
			{
				beginningOfTime = k;
			}
		}

		return beginningOfTime;
	}

	@Override
	public long getLatestRecordTime()
	{
		long beginningOfTime = Long.MIN_VALUE;

		for(String i : js.keySet())
		{
			long k = Long.valueOf(i);

			if(k > beginningOfTime)
			{
				beginningOfTime = k;
			}
		}

		return beginningOfTime;
	}

	private boolean within(long v, long from, long to)
	{
		return v >= from && v <= to;
	}

	@Override
	public int countRecords(long from, long to)
	{
		int c = 0;

		for(String i : js.keySet())
		{
			long k = Long.valueOf(i);
			c += within(k, from, to) ? 1 : 0;
		}

		return c;
	}

	@Override
	public GMap<Long, T> getRecords(long from, long to)
	{
		GMap<Long, T> v = new GMap<Long, T>();

		for(String i : js.keySet())
		{
			long k = Long.valueOf(i);

			if(within(k, from, to))
			{
				v.put(k, getRecord(k));
			}
		}

		return v;
	}

	@Override
	public int purgeRecordsBefore(long time)
	{
		int prg = 0;

		for(String i : new GList<String>(js.keySet()))
		{
			long k = Long.valueOf(i);

			if(within(k, Long.MIN_VALUE, time))
			{
				prg++;
				js.remove("" + k);
			}
		}

		return prg;
	}

	@Override
	public File getFile()
	{
		return recordFile;
	}

	private void write() throws IOException
	{
		recordFile.getParentFile().mkdirs();
		recordFile.createNewFile();
		FileWriter fw = new FileWriter(recordFile);
		PrintWriter pw = new PrintWriter(fw);
		pw.println(js.toString(4));
		pw.close();
	}

	private void read() throws IOException
	{
		FileReader fin = new FileReader(recordFile);
		BufferedReader bu = new BufferedReader(fin);
		String line;
		String content = "";

		while((line = bu.readLine()) != null)
		{
			content += line;
		}

		bu.close();
		js = new JSONObject(content);
	}
}
