package com.volmit.react.api;

import com.volmit.react.util.JSONArray;
import com.volmit.react.util.JSONObject;
import com.volmit.volume.lang.collections.GList;

public class StackTraceRecord extends Record<GList<StackTraceElement>>
{
	public StackTraceRecord(long recordTime, GList<StackTraceElement> object, String recordType)
	{
		super(recordTime, object, recordType);
	}

	@Override
	public JSONObject toJSON()
	{
		JSONObject jso = new JSONObject();
		JSONArray arr = new JSONArray();

		for(StackTraceElement i : getRecordObject())
		{
			JSONObject jsi = new JSONObject();
			jsi.put("line", i.getLineNumber());
			jsi.put("file", i.getFileName());
			jsi.put("class", i.getClassName());
			jsi.put("method", i.getMethodName());
			arr.put(jsi);
		}

		jso.put("trace", arr);

		return jso;
	}

	@Override
	public void fromJSON(JSONObject k)
	{
		GList<StackTraceElement> stl = new GList<StackTraceElement>();
		JSONArray arr = k.getJSONArray("trace");

		for(int i = 0; i < arr.length(); i++)
		{
			JSONObject jso = arr.getJSONObject(i);
			StackTraceElement ste = new StackTraceElement(jso.getString("class"), jso.getString("method"), jso.getString("file"), jso.getInt("line"));
			stl.add(ste);
		}

		object = stl;
	}
}
