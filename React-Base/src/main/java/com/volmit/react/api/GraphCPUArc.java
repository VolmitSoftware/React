package com.volmit.react.api;

import java.awt.Color;
import java.util.UUID;

import com.volmit.react.util.Average;
import com.volmit.react.util.F;
import com.volmit.react.util.M;
import com.volmit.react.util.Platform;

public class GraphCPUArc extends NormalGraph implements IGraph
{
	private byte fontColor;
	private byte backgroundColor;
	private double pct;
	private Average aax = new Average(20);
	private Average aay = new Average(40);
	private Average aaz = new Average(60);
	private long msx;
	private long lastf = M.ms();
	private BufferedFrame last;

	public GraphCPUArc(byte fontColor)
	{
		super("cpuGraph-" + UUID.randomUUID(), 1000);
		msx = M.ms();
		sample();
		this.fontColor = fontColor;
		this.backgroundColor = FrameColor.matchColor(FrameColor.getColor(fontColor));
	}

	private void sample()
	{
		if(M.ms() - msx > 500)
		{
			pct = Platform.CPU.getCPULoad();
			msx = M.ms();
		}
	}

	@Override
	public void onRender(BufferedFrame frame)
	{
		if(M.ms() - lastf < 750.0)
		{
			if(last != null)
			{
				frame.write(last);
				return;
			}
		}

		lastf = M.ms();
		sample();
		aax.put(pct);
		aay.put(aax.getAverage());
		aaz.put(aay.getAverage());
		frame.write(FrameColor.matchColor(10, 10, 10));
		frame.drawText((frame.getWidth() / 2) - (ReactFont.Font.getWidth("CPU") / 2), (frame.getHeight() / 2) - (ReactFont.Font.getHeight() + 4), ReactFont.Font, FrameColor.matchColor(Color.getHSBColor((float) aax.getAverage(), 1f, 1f)), "CPU");
		frame.drawText((frame.getWidth() / 2) - (ReactFont.Font.getWidth(F.pc(aax.getAverage())) / 2), frame.getHeight() / 2, ReactFont.Font, FrameColor.matchColor(Color.getHSBColor((float) aax.getAverage(), 1f, 1f)), F.pc(aax.getAverage()));

		for(int i = 0; i < frame.getWidth(); i++)
		{
			for(int j = 0; j < frame.getHeight(); j++)
			{
				if((i == 0 || j == 0) || (i == frame.getWidth() - 1 || j == frame.getHeight() - 1))
				{
					frame.write(i, j, FrameColor.DARK_GRAY);
				}
			}
		}

		last = frame;
	}

	public byte getFontColor()
	{
		return fontColor;
	}

	public void setFontColor(byte fontColor)
	{
		this.fontColor = fontColor;
	}

	public byte getBackgroundColor()
	{
		return backgroundColor;
	}

	public void setBackgroundColor(byte backgroundColor)
	{
		this.backgroundColor = backgroundColor;
	}
}
