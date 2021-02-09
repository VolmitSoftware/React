package com.volmit.react.api;

public class Notification
{
	private Note type;
	private String message;

	public Notification(Note type, String message)
	{
		this.type = type;
		this.message = message;
	}

	public Note getType()
	{
		return type;
	}

	public void setType(Note type)
	{
		this.type = type;
	}

	public String getMessage()
	{
		return message;
	}

	public void setMessage(String message)
	{
		this.message = message;
	}
}
