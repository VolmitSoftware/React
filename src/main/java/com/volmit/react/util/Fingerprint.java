package com.volmit.react.util;

import java.util.UUID;

public class Fingerprint
{
	public static String randomFingerprint(String h)
	{
		String k = "";
		
		k = k + UUID.randomUUID().toString();
		k = k.replaceAll("-", "d6a" + h);
		
		return k;
	}
}
