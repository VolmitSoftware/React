package com.volmit.react.util;

public interface DownloadMonitor
{
	void onDownloadStateChanged(Download download, DownloadState from, DownloadState to);

	void onDownloadStarted(Download download);

	void onDownloadFinished(Download download);

	void onDownloadFailed(Download download);

	void onDownloadUpdateProgress(Download download, long bytes, long totalBytes, double percentComplete);
}
