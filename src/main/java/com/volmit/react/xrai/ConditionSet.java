package com.volmit.react.xrai;

import com.volmit.react.util.JSONArray;
import com.volmit.volume.lang.collections.GList;

public class ConditionSet
{
	private GList<Condition> conditions;

	public ConditionSet()
	{
		this.conditions = new GList<Condition>();
	}

	public ConditionSet(JSONArray a)
	{
		this();

		for(int i = 0; i < a.length(); i++)
		{
			conditions.add(new Condition(a.getString(i)));
		}
	}

	public GList<Condition> getConditions()
	{
		return conditions;
	}

	public JSONArray toJSON()
	{
		JSONArray o = new JSONArray();

		for(Condition i : conditions)
		{
			o.put(i.toString());
		}

		return o;
	}

	public boolean isMet()
	{
		for(Condition i : conditions)
		{
			if(!i.isMet())
			{
				return false;
			}
		}

		return true;
	}
}
