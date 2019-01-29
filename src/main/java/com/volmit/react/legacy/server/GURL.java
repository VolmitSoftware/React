package com.volmit.react.legacy.server;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

public class GURL
{
	public static URL um(String s) throws MalformedURLException
	{
		return new URL(s);
	}
	
	public static UUID uidbytes(byte[] b)
	{
		return UUID.nameUUIDFromBytes(b);
	}
}
