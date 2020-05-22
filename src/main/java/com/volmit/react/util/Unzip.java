package com.volmit.react.util;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Unzip
{
    private UnzipState state;
    private UnzipType type;
    private UnzipStatus status;
    private UnzipMonitor monitor;
    private File file;
    private File outputDestination;
    private int bufferSize;
    private byte[] buffer;

    public Unzip(UnzipMonitor monitor, File file, File outputDestination, int bufferSize)
    {
        this.file = file;
        this.outputDestination = outputDestination;
        this.monitor = monitor;
        this.bufferSize = bufferSize;
        buffer = new byte[bufferSize];
        status = new UnzipStatus();
        state = UnzipState.IDLE;
        type = UnzipType.INDETERMINATE;
    }

    public void start() throws IOException
    {
        if(state.equals(UnzipState.UNZIPPING))
        {
            throw new IOException("Download already running!");
        }

        try
        {
            if(!outputDestination.exists()) outputDestination.mkdirs();

            long time = M.ms();
            int read = 0;
            long size = file.length();

            FileInputStream in = new FileInputStream(file);
            
            ZipInputStream zipin = new ZipInputStream(in);
            ZipEntry zipEntry = zipin.getNextEntry();
            String fileName = zipEntry.getName();
            UnzipState lastState = state;
            status.setBytesTotal(size);
            status.setBytesUnzipped(0);
            type = size <= 0 ? UnzipType.INDETERMINATE : UnzipType.DETERMINATE;
            state = UnzipState.UNZIPPING;
            monitor.onUnzipStateChanged(this, lastState, state);
            monitor.onUnzipStarted(this);

            while(zipEntry != null)
            {
                File newFile = new File(outputDestination + File.separator + fileName);
                System.out.println("Unzipping to "+newFile.getAbsolutePath());
                //create directories for sub directories in zip
                new File(newFile.getParent()).mkdirs();
                FileOutputStream out = new FileOutputStream(newFile);
                while ((read = in.read(buffer, 0, bufferSize)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.close();
                //close this ZipEntry
                zipin.closeEntry();
                zipEntry = zipin.getNextEntry();
                status.setBytesUnzipped(status.getBytesUnzipped() + read);
                status.setTimeElapsed(M.ms() - time);
                monitor.onUnzipUpdateProgress(this, status.getBytesUnzipped(), status.getBytesTotal(), status.getPercentCompleted());
            }

            in.close();
            lastState = state;
            state = UnzipState.FINISHED;
            monitor.onUnzipStateChanged(this, lastState, state);
            monitor.onUnzipFinished(this);
        }

        catch(Throwable e)
        {
            Ex.t(e);
            UnzipState lastState = state;
            state = UnzipState.FAILED;
            monitor.onUnzipStateChanged(this, lastState, state);
            monitor.onUnzipFailed(this);
            throw new IOException("Unzip Failed", e);
        }
    }

    public UnzipState getState()
    {
        return state;
    }

    public UnzipType getType()
    {
        return type;
    }

    public UnzipStatus getStatus()
    {
        return status;
    }

    public UnzipMonitor getMonitor()
    {
        return monitor;
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
