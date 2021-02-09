package com.volmit.react.api;

import primal.json.JSONObject;

public interface IRecord<T>
{
	String getRecordType();

	long getRecordTime();

	JSONObject toJSON();

	T getRecordObject();

	void fromJSON(JSONObject k);
}
