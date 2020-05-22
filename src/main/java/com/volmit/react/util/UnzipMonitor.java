package com.volmit.react.util;

public interface UnzipMonitor
{
	public void onUnzipStateChanged(Unzip unzip, UnzipState from, UnzipState to);

	public void onUnzipStarted(Unzip unzip);

	public void onUnzipFinished(Unzip unzip);

	public void onUnzipFailed(Unzip unzip);

	public void onUnzipUpdateProgress(Unzip unzip, long bytes, long totalBytes, double percentComplete);
}
