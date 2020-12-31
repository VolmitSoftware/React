package com.volmit.react.util;

public interface UnzipMonitor
{
	void onUnzipStateChanged(Unzip unzip, UnzipState from, UnzipState to);

	void onUnzipStarted(Unzip unzip);

	void onUnzipFinished(Unzip unzip);

	void onUnzipFailed(Unzip unzip);

	void onUnzipUpdateProgress(Unzip unzip, long bytes, long totalBytes, double percentComplete);
}
