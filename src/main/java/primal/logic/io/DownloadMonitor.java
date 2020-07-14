package primal.logic.io;

import primal.logic.io.DL.DownloadState;

@FunctionalInterface
public interface DownloadMonitor 
{
	public void onUpdate(DownloadState state, double progress, long elapsed, long estimated, long bps, long iobps, long size, long downloaded, long buffer, double bufferuse);
}
