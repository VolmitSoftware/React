package com.volmit.react.util;

public class DownloadStatus
{
	private long bytesDownloaded;
	private long bytesTotal;
	private long bytesPerSecond;
	private long timeElapsed;

	public DownloadStatus()
	{
		this.bytesDownloaded = 0;
		this.bytesTotal = 0;
		this.bytesPerSecond = 0;
		this.timeElapsed = 0;
	}

	public double getPercentCompleted()
	{
		return (double) bytesDownloaded / (double) bytesTotal;
	}

	public long getBytesDownloaded()
	{
		return bytesDownloaded;
	}

	public void setBytesDownloaded(long bytesDownloaded)
	{
		this.bytesDownloaded = bytesDownloaded;
	}

	public long getBytesTotal()
	{
		return bytesTotal;
	}

	public void setBytesTotal(long bytesTotal)
	{
		this.bytesTotal = bytesTotal;
	}

	public long getBytesPerSecond()
	{
		return bytesPerSecond;
	}

	public void setBytesPerSecond(long bytesPerSecond)
	{
		this.bytesPerSecond = bytesPerSecond;
	}

	public long getTimeElapsed()
	{
		return timeElapsed;
	}

	public void setTimeElapsed(long timeElapsed)
	{
		this.timeElapsed = timeElapsed;
	}
}
