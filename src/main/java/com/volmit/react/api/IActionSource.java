package com.volmit.react.api;

public interface IActionSource
{
	public void sendResponse(String r);

	public void sendResponseSuccess(String r);

	public void sendResponseError(String r);

	public void sendResponseActing(String r);
}
