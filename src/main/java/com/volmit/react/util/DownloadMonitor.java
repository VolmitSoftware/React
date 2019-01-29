package com.volmit.react.util;

public interface DownloadMonitor
{
	public void onDownloadStateChanged(Download download, DownloadState from, DownloadState to);

	public void onDownloadStarted(Download download);

	public void onDownloadFinished(Download download);

	public void onDownloadFailed(Download download);

	public void onDownloadUpdateProgress(Download download, long bytes, long totalBytes, double percentComplete);
}
