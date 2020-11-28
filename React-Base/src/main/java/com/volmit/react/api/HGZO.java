package com.volmit.react.api;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

public class HGZO extends GZIPOutputStream
{
	public HGZO(OutputStream out) throws IOException
	{
		super(out);
		def.setLevel(Deflater.BEST_COMPRESSION);
		def.setStrategy(Deflater.FILTERED);
	}

}
