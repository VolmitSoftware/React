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
            throw new IOException("Unzip already running!");
        }
        if(!outputDestination.exists()) outputDestination.mkdirs();
        FileInputStream fis;
        try
        {
            fis = new FileInputStream(file);
            ZipInputStream zis = new ZipInputStream(fis);
            ZipEntry ze = zis.getNextEntry();

            long time = M.ms();
            int read = 0;
            long size = file.length();

            UnzipState lastState = state;
            status.setBytesTotal(size);
            status.setBytesUnzipped(0);
            type = size <= 0 ? UnzipType.INDETERMINATE : UnzipType.DETERMINATE;
            state = UnzipState.UNZIPPING;
            monitor.onUnzipStateChanged(this, lastState, state);
            monitor.onUnzipStarted(this);

            while(ze != null){
                String fileName = ze.getName();
                File newFile = new File(outputDestination + File.separator + fileName);
                System.out.println("Unzipping to "+newFile.getAbsolutePath());
                //create directories for sub directories in zip
                new File(newFile.getParent()).mkdirs();
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
                //close this ZipEntry
                zis.closeEntry();
                ze = zis.getNextEntry();
            }

            zis.closeEntry();
            zis.close();
            fis.close();
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
