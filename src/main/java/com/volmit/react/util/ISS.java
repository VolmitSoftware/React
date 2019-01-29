package com.volmit.react.util;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import javax.crypto.Cipher;

import com.volmit.volume.lang.collections.GList;

/**
 * A pretty good input stream
 *
 * @author cyberpwn
 *
 */
public class ISS extends InputStream
{
	private InputStream in;
	private final StreamBuilder bu;
	private boolean built;

	/**
	 * Creates an ISS based on the input stream
	 *
	 * @param in
	 *            the parent input stream
	 */
	public ISS(InputStream in)
	{
		this.in = in;
		this.bu = new StreamBuilder();
		this.built = false;
	}

	/**
	 * Creates an ISS based on a byte array
	 *
	 * @param bytes
	 *            the data
	 */
	public ISS(byte[] bytes)
	{
		this(new ByteArrayInputStream(bytes));
	}

	/**
	 * Create a buffer on top of the parent stream (this will be the new parent)
	 *
	 * @param bufferSize
	 *            the buffer size
	 * @return this ISS
	 */
	public ISS buffer(int bufferSize)
	{
		bu.bindBuffer(bufferSize);
		return this;
	}

	public final GList<String> readStringList() throws IOException
	{
		int f = readInt();
		GList<String> st = new GList<String>();

		for(int i = 0; i < f; i++)
		{
			st.add(readString());
		}

		return st;
	}

	public final GList<Double> readDoubleList() throws IOException
	{
		int f = readInt();
		GList<Double> st = new GList<Double>();

		for(int i = 0; i < f; i++)
		{
			st.add(readDouble());
		}

		return st;
	}

	public final GList<Long> readLongList() throws IOException
	{
		int f = readInt();
		GList<Long> st = new GList<Long>();

		for(int i = 0; i < f; i++)
		{
			st.add(readLong());
		}

		return st;
	}

	public final GList<Streamable> readStreamableList(Streamable type) throws Exception
	{
		int f = readInt();
		GList<Streamable> st = new GList<Streamable>();

		for(int i = 0; i < f; i++)
		{
			Streamable newf = type.getClass().getConstructor().newInstance();
			read(newf);
			st.add(newf);
		}

		return st;
	}

	/**
	 * Create a cipherInputStream on top of the parent stream
	 *
	 * @param cipher
	 *            the cipher used to decrypt this stream
	 * @return this ISS
	 */
	public ISS decrypt(Cipher cipher)
	{
		bu.bindCipher(cipher);
		return this;
	}

	/**
	 * Create a deflating input stream on top of the parent stream
	 *
	 * @return this ISS
	 */
	public ISS gzip()
	{
		bu.bindGZIP();
		return this;
	}

	private void build() throws IOException
	{
		if(!built)
		{
			built = true;
			in = bu.constructInput(in);
		}
	}

	@Override
	public int read() throws IOException
	{
		build();
		int m = in.read();
		return m;
	}

	/**
	 * Reads a boolean (byte)
	 *
	 * @return true or fucking false
	 * @throws IOException
	 *             someone fried your raid controller
	 */
	public final boolean readBoolean() throws IOException
	{
		int ch = read();

		if(ch < 0)
		{
			throw new EOFException();
		}

		return (ch != 0);
	}

	/**
	 * Reads a byte
	 *
	 * @return a signed java byte
	 * @throws IOException
	 *             you kicked the machine too hard
	 */
	public final byte readByte() throws IOException
	{
		int ch = read();

		if(ch < 0)
		{
			throw new EOFException();
		}

		return (byte) (ch);
	}

	/**
	 * Reads a short
	 *
	 * @return a signed java short
	 * @throws IOException
	 *             you really do have a loose ethernet port dont you
	 */
	public final short readShort() throws IOException
	{
		int ch1 = read();
		int ch2 = read();

		if((ch1 | ch2) < 0)
		{
			throw new EOFException();
		}

		return (short) ((ch1 << 8) + (ch2 << 0));
	}

	/**
	 * Reads a char
	 *
	 * @return a signed java character
	 * @throws IOException
	 *             your drives are full
	 */
	public final char readChar() throws IOException
	{
		int ch1 = read();
		int ch2 = read();

		if((ch1 | ch2) < 0)
		{
			throw new EOFException();
		}

		return (char) ((ch1 << 8) + (ch2 << 0));
	}

	/**
	 * Reads an integer
	 *
	 * @return a signed java integer
	 * @throws IOException
	 *             stop using your hotspot
	 */
	public final int readInt() throws IOException
	{
		int ch1 = read();
		int ch2 = read();
		int ch3 = read();
		int ch4 = read();

		if((ch1 | ch2 | ch3 | ch4) < 0)
		{
			throw new EOFException();
		}

		return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
	}

	private byte readBuffer[] = new byte[8];

	/**
	 * Read all of it, dont return some of it. Wait (block)
	 *
	 * @param b
	 *            the bytes to fill
	 * @throws IOException
	 *             shit happens
	 */
	public final void readFully(byte b[]) throws IOException
	{
		readFully(b, 0, b.length);
	}

	/**
	 * Read all of it, dont return some of it. Wait (block)
	 *
	 * @param b
	 *            the bytes to fill
	 * @param off
	 *            the offset
	 * @param len
	 *            the length to read
	 * @throws IOException
	 *             shit happens
	 */
	public final void readFully(byte b[], int off, int len) throws IOException
	{
		if(len < 0)
		{
			throw new IndexOutOfBoundsException();
		}

		int n = 0;

		while(n < len)
		{
			int count = read(b, off + n, len - n);
			if(count < 0)
			{
				throw new EOFException();
			}

			n += count;
		}
	}

	@Override
	public int read(byte b[]) throws IOException
	{
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte b[], int off, int len) throws IOException
	{
		if(b == null)
		{
			throw new NullPointerException();
		}

		else if(off < 0 || len < 0 || len > b.length - off)
		{
			throw new IndexOutOfBoundsException();
		}

		else if(len == 0)
		{
			return 0;
		}

		int c = read();

		if(c == -1)
		{
			return -1;
		}

		b[off] = (byte) c;

		int i = 1;

		try
		{
			for(; i < len; i++)
			{
				c = read();

				if(c == -1)
				{
					break;
				}

				b[off + i] = (byte) c;
			}
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}

		return i;
	}

	/**
	 * Read a long
	 *
	 * @return a java signed long
	 * @throws IOException
	 *             shit happens
	 */
	public final long readLong() throws IOException
	{
		readFully(readBuffer, 0, 8);
		return (((long) readBuffer[0] << 56) + ((long) (readBuffer[1] & 255) << 48) + ((long) (readBuffer[2] & 255) << 40) + ((long) (readBuffer[3] & 255) << 32) + ((long) (readBuffer[4] & 255) << 24) + ((readBuffer[5] & 255) << 16) + ((readBuffer[6] & 255) << 8) + ((readBuffer[7] & 255) << 0));
	}

	/**
	 * Read a float
	 *
	 * @return a java signed float
	 * @throws IOException
	 *             shit happens
	 */
	public final float readFloat() throws IOException
	{
		return Float.intBitsToFloat(readInt());
	}

	/**
	 * Read a double
	 *
	 * @return a java signed double
	 * @throws IOException
	 *             shit happens
	 */
	public final double readDouble() throws IOException
	{
		return Double.longBitsToDouble(readLong());
	}

	/**
	 * Read a string (chars)
	 *
	 * @return a string
	 * @throws IOException
	 *             shit happens
	 */
	public final String readString() throws IOException
	{
		int len = readInt();
		String v = "";

		for(int i = 0; i < len; i++)
		{
			v += readChar();
		}

		return v;
	}

	/**
	 * Read a streamable object
	 *
	 * @param s
	 *            the streamable object
	 * @throws Exception
	 */
	public final void read(Streamable s) throws Exception
	{
		s.fromBytes(this);
	}

	@Override
	public void close() throws IOException
	{
		in.close();
	}
}
