package com.volmit.react;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class Lang
{
	private static final String BUNDLE_NAME = "com.volmit.react.lang"; //$NON-NLS-1$
	public static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);
	public static ResourceBundle PRIMARY_BUNDLE = RESOURCE_BUNDLE;

	private Lang()
	{

	}

	public static String getString(String key)
	{
		try
		{
			try
			{
				return PRIMARY_BUNDLE.getString(key);
			}

			catch(MissingResourceException e)
			{
				return RESOURCE_BUNDLE.getString(key);
			}
		}

		catch(MissingResourceException e)
		{
			return '!' + key + '!';
		}
	}
}
