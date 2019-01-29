package com.volmit.react.api;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

import com.volmit.react.util.M;
import com.volmit.volume.lang.collections.GList;

public class Datalog
{
	private File file;
	private int interval;
	private long start;
	private int size;
	private int max;
	private int itrf;
	private int written;
	private int read;
	private DataOutputStream dos;
	private DataInputStream din;

	public Datalog(File file)
	{
		this.file = file;
		written = 0;
		max = 0;
		itrf = 0;
		read = 0;
	}

	public void openReadStream() throws IOException
	{
		FileInputStream fin = new FileInputStream(file);
		GZIPInputStream gzi = new GZIPInputStream(fin);
		BufferedInputStream bin = new BufferedInputStream(gzi, 8192);
		din = new DataInputStream(bin);
		start = din.readLong();
		interval = din.readInt();
		size = din.readInt();
		itrf = din.readInt();
	}

	public void skipStream(int itr) throws IOException
	{
		din.skip(4 * size * itr);
		read += itr;
	}

	public void skipStream() throws IOException
	{
		din.skip(4 * size);
		read++;
	}

	public GList<Double> readStream() throws IOException
	{
		GList<Double> gg = new GList<Double>();

		for(int i = 0; i < itrf; i++)
		{
			gg.add((double) din.readFloat() * 32);
		}

		read++;

		return gg;
	}

	public boolean hasNext()
	{
		return read < size;
	}

	public boolean hasNext(int howMany)
	{
		return read + howMany < size;
	}

	public void openStream(int interval, int itr, int size) throws IOException
	{
		openStream(interval, itr, size, M.ms());
	}

	public int remaining()
	{
		return max - written;
	}

	public void openStream(int interval, int itr, int size, long start) throws IOException
	{
		this.interval = interval;
		itrf = itr;
		max = size;
		file.getParentFile().mkdirs();
		FileOutputStream fos = new FileOutputStream(file);
		HGZO gzo = new HGZO(fos);
		dos = new DataOutputStream(gzo);
		dos.writeLong(start);
		dos.writeInt(interval);
		dos.writeInt(size);
		dos.writeInt(itr);
	}

	public void stream(GList<Double> doubles) throws IOException
	{
		if(doubles.size() != itrf)
		{
			throw new IllegalArgumentException("Expected " + itrf + " doubles. (" + doubles.size() + ")");
		}

		for(Double i : doubles)
		{
			dos.writeFloat(i.floatValue() / 32);
		}

		if(written % 20 == 0)
		{
			dos.flush();
		}

		written++;
	}

	public void closeRead() throws IOException
	{
		din.close();
	}

	public boolean close() throws IOException
	{
		GList<Double> dummy = new GList<Double>();
		dummy.fill(-1D, itrf);
		boolean full = true;

		while(written < max)
		{
			stream(dummy);
			full = false;
		}

		dos.close();
		return full;
	}

	public File getFile()
	{
		return file;
	}

	public long getIntervalMS()
	{
		return (getInterval() < 1 ? 1 : getInterval()) * 50;
	}

	public int getInterval()
	{
		return interval;
	}

	public long getStart()
	{
		return start;
	}

	public int getSize()
	{
		return size;
	}

	public int getMax()
	{
		return max;
	}

	public int getItrf()
	{
		return itrf;
	}

	public int getWritten()
	{
		return written;
	}

	public int getRead()
	{
		return read;
	}

	public DataOutputStream getDos()
	{
		return dos;
	}

	public DataInputStream getDin()
	{
		return din;
	}
}
