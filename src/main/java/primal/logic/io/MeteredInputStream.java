package primal.logic.io;

import java.io.IOException;
import java.io.InputStream;

public class MeteredInputStream extends InputStream
{
	private InputStream os;
	private long written;
	private long totalWritten;
	private long since;
	private boolean auto;
	private long interval;
	private long bps;
	
	public MeteredInputStream(InputStream os, long interval)
	{
		this.os = os;
		written = 0;
		totalWritten = 0;
		auto = true;
		this.interval = interval;
		bps = 0;
		since = System.currentTimeMillis();
	}
	
	public MeteredInputStream(InputStream os)
	{
		this(os, 100);
		auto = false;
	}

	@Override
	public int read() throws IOException 
	{
		written++;
		totalWritten++;
		
		if(auto && System.currentTimeMillis() - getSince() > interval)
		{
			pollRead();
		}
		
		return os.read();
	}
	
	public long getSince()
	{
		return since;
	}
	
	public long getRead()
	{
		return written;
	}
	
	public long pollRead()
	{
		long w = written;
		written = 0;
		double secondsElapsedSince = (double) (System.currentTimeMillis() - since) / 1000.0;
		bps = (long) ((double) w / secondsElapsedSince);
		since = System.currentTimeMillis();
		
		return w;
	}

	public void close() throws IOException
	{
		os.close();
	}

	public boolean isAuto()
	{
		return auto;
	}

	public void setAuto(boolean auto) 
	{
		this.auto = auto;
	}

	public long getInterval()
	{
		return interval;
	}

	public void setInterval(long interval)
	{
		this.interval = interval;
	}

	public long getTotalRead() 
	{
		return totalWritten;
	}

	public long getBps() 
	{
		return bps;
	}
}
