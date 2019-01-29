package com.volmit.react.util;

public interface Streamable
{
	public void toBytes(OSS out) throws Exception;

	public void fromBytes(ISS in) throws Exception;
}
