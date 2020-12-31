package com.volmit.react.api;

import java.awt.Color;
import java.util.UUID;

import com.volmit.react.util.Average;
import com.volmit.react.util.F;
import com.volmit.react.util.M;

public class GraphMemoryArc extends NormalGraph implements IGraph
{
	private byte fontColor;
	private byte backgroundColor;
	private double pct;
	private Average aax = new Average(20);
	private long msx;
	private long of;

	public GraphMemoryArc(byte fontColor)
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
			of = (long) SampledType.MAXMEM.get().getValue();
			pct = SampledType.MEM.get().getValue() / of;
			msx = M.ms();
		}
	}

	@Override
	public void onRender(BufferedFrame frame)
	{
		sample();
		aax.put(pct);
		frame.write(FrameColor.matchColor(10, 10, 10));

		String mem = F.memSize((long) (of * aax.getAverage()), 0);
		frame.drawText((frame.getWidth() / 2) - (ReactFont.Font.getWidth("MEM") / 2), (frame.getHeight() / 2) - (ReactFont.Font.getHeight() + 4), ReactFont.Font, FrameColor.matchColor(Color.getHSBColor((float) aax.getAverage(), 1f, 1f)), "MEM");
		frame.drawText((frame.getWidth() / 2) - (ReactFont.Font.getWidth(F.pc(aax.getAverage())) / 2), (frame.getHeight() / 2), ReactFont.Font, FrameColor.matchColor(Color.getHSBColor((float) aax.getAverage(), 1f, 1f)), F.pc(aax.getAverage()));
		frame.drawText((frame.getWidth() / 2) - (ReactFont.Font.getWidth(mem) / 2), (frame.getHeight() / 2) + (ReactFont.Font.getHeight() + 4), ReactFont.Font, FrameColor.matchColor(Color.getHSBColor((float) aax.getAverage(), 1f, 1f)), mem);

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
