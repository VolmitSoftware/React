package com.volmit.react.xmonitor;

import com.volmit.react.api.SampledType;
import com.volmit.react.util.JSONArray;
import com.volmit.react.util.JSONObject;
import com.volmit.volume.lang.collections.GList;

public class TitleHeader
{
	private String title;
	private SampledType head;
	private GList<SampledType> fields;

	public TitleHeader(String title, SampledType head)
	{
		this.title = title;
		this.head = head;
		fields = new GList<SampledType>();
	}

	public TitleHeader(JSONObject json)
	{
		title = json.getString("title");
		head = SampledType.valueOf(json.getString("head").toUpperCase());
		fields = new GList<SampledType>();
		JSONArray ja = json.getJSONArray("fields");

		for(int i = 0; i < ja.length(); i++)
		{
			fields.add(SampledType.valueOf(ja.getString(i).toUpperCase()));
		}
	}

	public void f(SampledType t)
	{
		fields.add(t);
	}

	public String getTitle()
	{
		return title;
	}

	public void setTitle(String title)
	{
		this.title = title;
	}

	public SampledType getHead()
	{
		return head;
	}

	public void setHead(SampledType head)
	{
		this.head = head;
	}

	public GList<SampledType> getFields()
	{
		return fields;
	}

	public void setFields(GList<SampledType> fields)
	{
		this.fields = fields;
	}

	public JSONObject toJSON()
	{
		JSONObject o = new JSONObject();

		o.put("title", title);
		o.put("head", head.name().toLowerCase());

		JSONArray ja = new JSONArray();

		for(SampledType i : fields)
		{
			ja.put(i.name().toLowerCase());
		}

		o.put("fields", ja);

		return o;
	}
}
