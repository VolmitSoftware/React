package com.volmit.react.util;

/**
 * Represents a string that can be changed even if final
 * 
 * @author cyberpwn
 */
public class FinalString
{
	private String string;
	
	/**
	 * Create a final string
	 * 
	 * @param string
	 *            the string
	 */
	public FinalString(String string)
	{
		this.string = string;
	}
	
	/**
	 * Get the string
	 * 
	 * @return the string
	 */
	public String get()
	{
		return string;
	}
	
	/**
	 * Set the string
	 * 
	 * @param string
	 *            the string
	 */
	public void set(String string)
	{
		this.string = string;
	}
}
