package com.volmit.react.util;

public interface Streamable
{
	void toBytes(OSS out) throws Exception;

	void fromBytes(ISS in) throws Exception;
}
