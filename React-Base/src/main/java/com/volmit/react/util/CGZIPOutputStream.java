package com.volmit.react.util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

public class CGZIPOutputStream extends GZIPOutputStream
{
	public CGZIPOutputStream(OutputStream out, int level) throws IOException
	{
		super(out);
		this.def = new Deflater(level, false);
	}
}
