package com.volmit.react.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.OutputStream;

import javax.crypto.Cipher;

import com.volmit.volume.lang.collections.GList;

public class OSS extends OutputStream
{
	private OutputStream out;
	private OutputStream gen;
	private final StreamBuilder bu;
	private boolean built;

	public OSS(OutputStream out)
	{
		this.out = out;
		this.gen = out;
		this.bu = new StreamBuilder();
		this.built = false;
	}

	public OSS()
	{
		this(new ByteArrayOutputStream());
	}

	public OSS buffer(int bufferSize)
	{
		bu.bindBuffer(bufferSize);
		return this;
	}

	public OSS gzip(int compressionLevel)
	{
		bu.bindGZIP(compressionLevel);
		return this;
	}

	public OSS encrypt(Cipher cipher)
	{
		bu.bindCipher(cipher);
		return this;
	}

	private void build() throws IOException
	{
		if(!built)
		{
			built = true;
			out = bu.constructOutput(out);
		}
	}

	public final void writeBoolean(boolean v) throws IOException
	{
		write(v ? 1 : 0);
	}

	public final void writeByte(int v) throws IOException
	{
		write(v);
	}

	public final void writeShort(int v) throws IOException
	{
		write((v >>> 8) & 0xFF);
		write((v >>> 0) & 0xFF);
	}

	public final void writeChar(int v) throws IOException
	{
		write((v >>> 8) & 0xFF);
		write((v >>> 0) & 0xFF);
	}

	public final void writeInt(int v) throws IOException
	{
		write((v >>> 24) & 0xFF);
		write((v >>> 16) & 0xFF);
		write((v >>> 8) & 0xFF);
		write((v >>> 0) & 0xFF);
	}

	public final void writeStringList(GList<String> s) throws IOException
	{
		writeInt(s.size());

		for(String i : s)
		{
			writeString(i);
		}
	}

	public final void writeDoubleList(GList<Double> s) throws IOException
	{
		writeInt(s.size());

		for(Double i : s)
		{
			writeDouble(i);
		}
	}

	public final void writeLongList(GList<Long> s) throws IOException
	{
		writeInt(s.size());

		for(Long i : s)
		{
			writeLong(i);
		}
	}

	public final void writeStreamableList(GList<Streamable> s) throws Exception
	{
		writeInt(s.size());

		for(Streamable i : s)
		{
			write(i);
		}
	}

	private byte writeBuffer[] = new byte[8];

	public final void writeLong(long v) throws IOException
	{
		writeBuffer[1] = (byte) (v >>> 48);
		writeBuffer[2] = (byte) (v >>> 40);
		writeBuffer[3] = (byte) (v >>> 32);
		writeBuffer[4] = (byte) (v >>> 24);
		writeBuffer[5] = (byte) (v >>> 16);
		writeBuffer[6] = (byte) (v >>> 8);
		writeBuffer[7] = (byte) (v >>> 0);
		write(writeBuffer, 0, 8);
	}

	public final void writeFloat(float v) throws IOException
	{
		writeInt(Float.floatToIntBits(v));
	}

	public final void writeDouble(double v) throws IOException
	{
		writeLong(Double.doubleToLongBits(v));
	}

	public final void writeString(String s) throws IOException
	{
		int len = s.length();

		writeInt(len);

		for(int i = 0; i < len; i++)
		{
			int v = s.charAt(i);
			writeChar(v);
		}
	}

	public void db()
	{

	}

	@Override
	public void write(int b) throws IOException
	{
		build();
		out.write(b);
	}

	/**
	 * Write a streamable object
	 *
	 * @param s
	 *            the object
	 * @throws Exception
	 */
	public void write(Streamable s) throws Exception
	{
		OSS dout = new OSS();
		s.toBytes(dout);
		write(dout.getBytes());
	}

	public final byte[] getBytes() throws IOException
	{
		if(gen instanceof ByteArrayOutputStream)
		{
			flush();
			return ((ByteArrayOutputStream) gen).toByteArray();
		}

		throw new InvalidClassException("The initial output stream must be a ByteArrayOutputStream");
	}

	@Override
	public void close() throws IOException
	{
		out.close();
	}

	@Override
	public void flush() throws IOException
	{
		out.flush();
	}
}
