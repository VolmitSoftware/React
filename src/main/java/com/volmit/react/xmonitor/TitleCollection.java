package com.volmit.react.xmonitor;

import com.volmit.react.util.JSONArray;
import com.volmit.volume.lang.collections.GList;

public class TitleCollection
{
	private GList<TitleHeader> headers;

	public TitleCollection()
	{
		headers = new GList<TitleHeader>();
	}

	public TitleCollection(JSONArray j)
	{
		this();

		for(int i = 0; i < j.length(); i++)
		{
			headers.add(new TitleHeader(j.getJSONObject(i)));
		}
	}

	public JSONArray toJSON()
	{
		JSONArray ja = new JSONArray();

		for(TitleHeader i : headers)
		{
			ja.put(i.toJSON());
		}

		return ja;
	}

	public GList<TitleHeader> getHeaders()
	{
		return headers;
	}

	public void setHeaders(GList<TitleHeader> headers)
	{
		this.headers = headers;
	}
}
