package com.volmit.react.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

public class Download
{
	private DownloadState state;
	private DownloadType type;
	private DownloadStatus status;
	private DownloadMonitor monitor;
	private URL url;
	private File file;
	private int bufferSize;
	private byte[] buffer;

	public Download(DownloadMonitor monitor, URL url, File file, int bufferSize)
	{
		this.url = url;
		this.file = file;
		this.monitor = monitor;
		this.bufferSize = bufferSize;
		buffer = new byte[bufferSize];
		status = new DownloadStatus();
		state = DownloadState.IDLE;
		type = DownloadType.INDETERMINATE;
	}

	public void start() throws IOException
	{
		if(state.equals(DownloadState.DOWNLOADING))
		{
			throw new IOException("Download already running!");
		}

		try
		{
			URLConnection conn = url.openConnection();
			long size = conn.getContentLengthLong();
			long time = M.ms();
			int read = 0;
			InputStream in = new BufferedInputStream(url.openStream());
			FileOutputStream out = new FileOutputStream(file);
			DownloadState lastState = state;
			status.setBytesTotal(size);
			status.setBytesDownloaded(0);
			type = size <= 0 ? DownloadType.INDETERMINATE : DownloadType.DETERMINATE;
			state = DownloadState.DOWNLOADING;
			monitor.onDownloadStateChanged(this, lastState, state);
			monitor.onDownloadStarted(this);

			while((read = in.read(buffer, 0, bufferSize)) != -1)
			{
				out.write(buffer, 0, read);
				status.setBytesDownloaded(status.getBytesDownloaded() + read);
				status.setTimeElapsed(M.ms() - time);
				monitor.onDownloadUpdateProgress(this, status.getBytesDownloaded(), status.getBytesTotal(), status.getPercentCompleted());
			}

			in.close();
			out.close();
			lastState = state;
			state = DownloadState.FINISHED;
			monitor.onDownloadStateChanged(this, lastState, state);
			monitor.onDownloadFinished(this);
		}

		catch(Throwable e)
		{
			Ex.t(e);
			DownloadState lastState = state;
			state = DownloadState.FAILED;
			monitor.onDownloadStateChanged(this, lastState, state);
			monitor.onDownloadFailed(this);
			throw new IOException("Download Failed", e);
		}
	}

	public DownloadState getState()
	{
		return state;
	}

	public DownloadType getType()
	{
		return type;
	}

	public DownloadStatus getStatus()
	{
		return status;
	}

	public DownloadMonitor getMonitor()
	{
		return monitor;
	}

	public URL getUrl()
	{
		return url;
	}

	public File getFile()
	{
		return file;
	}

	public int getBufferSize()
	{
		return bufferSize;
	}
}
