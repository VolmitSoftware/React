package com.volmit.react.util;

public class UnzipStatus {
    private long bytesUnzipped;
    private long bytesTotal;
    private long bytesPerSecond;
    private long timeElapsed;

    public UnzipStatus()
    {
        this.bytesUnzipped = 0;
        this.bytesTotal = 0;
        this.bytesPerSecond = 0;
        this.timeElapsed = 0;
    }

    public double getPercentCompleted()
    {
        return (double) bytesUnzipped / (double) bytesTotal;
    }

    public long getBytesUnzipped()
    {
        return bytesUnzipped;
    }

    public void setBytesUnzipped(long bytesDownloaded)
    {
        this.bytesUnzipped = bytesDownloaded;
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
