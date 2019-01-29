package com.volmit.react.util;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;

import com.volmit.react.Config;

/**
 * Paste to the web
 *
 * @author cyberpwn
 */
public class Paste
{
	public static String lastKey;

	/**
	 * Paste to throw.volmit.com/
	 *
	 * @param s
	 *            the paste text (use newline chars for new lines)
	 * @return the url to access the paste
	 * @throws org.json.simple.parser.ParseException
	 * @throws Exception
	 *             shit happens
	 */
	public static String paste(String toPaste) throws IOException, ParseException, org.json.simple.parser.ParseException
	{
		if(Config.SAFE_MODE_NETWORKING)
		{
			return "http://SAFEMODE-NETWORKING-ENABLED";
		}

		HttpURLConnection hastebin = (HttpURLConnection) new URL("http://paste.volmit.com/documents").openConnection();
		hastebin.setRequestMethod("POST");
		hastebin.setDoOutput(true);
		hastebin.setDoInput(true);
		DataOutputStream dos = new DataOutputStream(hastebin.getOutputStream());
		dos.writeBytes(toPaste);
		dos.flush();
		dos.close();
		BufferedReader rd = new BufferedReader(new InputStreamReader(hastebin.getInputStream()));
		JSONObject json = new JSONObject(rd.readLine());

		lastKey = json.get("key") + "";
		return "http://paste.volmit.com/" + json.get("key");
	}
}
