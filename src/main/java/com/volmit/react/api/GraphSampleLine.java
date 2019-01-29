package com.volmit.react.api;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.util.Average;
import com.volmit.react.util.M;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;

public class GraphSampleLine extends NormalGraph implements IGraph
{
	private double lx;
	private double ly;
	private ISampler sampler;
	private IFormatter formatter;
	private byte graphColor;
	private byte backgroundColor;
	private byte borderColor;
	private byte textColor;
	public int ticksLeftTitle;
	public int ticksOutTitle;
	public double nextPoint;
	private BufferedFrame last;
	private long lastf = M.ms();

	public GraphSampleLine(ISampler sampler, IFormatter formatter, long timeViewport)
	{
		super(sampler.getID(), timeViewport);
		this.sampler = sampler;
		this.formatter = formatter;
		graphColor = FrameColor.RED;
		backgroundColor = FrameColor.matchColor(10, 10, 10);
		textColor = FrameColor.WHITE;
		borderColor = FrameColor.DARK_GRAY;
		nextPoint = 0;
		lx = 0;
		ly = 0;
	}

	public void sample()
	{
		getPlotBoard().plot(M.ms(), sampler.getValue());
	}

	@Override
	public void onRender(BufferedFrame frame)
	{
		if(M.ms() - lastf < 1000.0 / (double) (Gate.isLowMemory() ? Config.MAP_FPS / 10 : Config.MAP_FPS))
		{
			if(last != null)
			{
				frame.write(last);
				return;
			}
		}

		lastf = M.ms();

		byte dk = FrameColor.matchColor(FrameColor.getColor(graphColor).darker().darker().darker());
		byte dt = FrameColor.matchColor(FrameColor.getColor(textColor).darker().darker().darker());
		GMap<Long, Double> map = getPlotBoard().getBetween(M.ms() - getTimeViewport(), M.ms());
		GList<Long> da = map.k();
		da.sort();
		scaleFor(map.v());
		Average aa = new Average(2);
		int dlast = -1;
		int wlast = -1;
		double yv = 0;
		double xv = 0;
		frame.write(backgroundColor);
		double value = -1;
		int pixX = -1;

		for(int i = 0; i < 12; i++)
		{
			if(!da.isEmpty())
			{
				aa.put(da.get(0));
			}
		}

		for(int i = 0; i < da.size(); i++)
		{
			double val = map.get(da.get(i));
			double pct = M.clip(1.0 - (val / getMax()), 0, 1);
			aa.put(pct);
			double scf = (double) frame.getWidth() / (double) da.size();
			int d = (int) (M.clip(aa.getAverage(), 0, 1.0) * (frame.getHeight() - 1));
			int w = (int) ((double) i * scf);
			d = (int) M.clip(d, 2, frame.getHeight() - 2);
			xv = w;
			yv = d;
			frame.write(w + 1, d + 1, dk);
			frame.write(w, d, graphColor);

			if(i == da.size() - 1)
			{
				pixX = w;
				value = val;
			}

			if(wlast >= 0)
			{
				if(wlast > w)
				{
					for(int j = wlast; j > w; j--)
					{
						frame.write(j + 1, d + 1, dk);
						frame.write(j, d, graphColor);
					}
				}

				if(wlast < w)
				{
					for(int j = w; j > wlast; j--)
					{
						frame.write(j + 1, d + 1, dk);
						frame.write(j, d, graphColor);
					}
				}
			}

			if(dlast >= 0)
			{
				if(dlast > d)
				{
					for(int j = dlast; j > d; j--)
					{
						frame.write((int) ((double) i * scf) + 1, j + 1, dk);
						frame.write((int) ((double) i * scf), j, graphColor);
					}
				}

				if(dlast < d)
				{
					for(int j = d; j > dlast; j--)
					{
						frame.write((int) ((double) i * scf) + 1, j + 1, dk);
						frame.write((int) ((double) i * scf), j, graphColor);
					}
				}
			}

			dlast = d;
			wlast = (int) ((double) i * scf);
		}

		if(Math.abs(xv - lx) > 0.01)
		{
			if(lx > xv)
			{
				lx -= ((double) Math.abs(xv - lx) / 10.0);
			}

			if(lx < xv)
			{
				lx += ((double) Math.abs(xv - lx) / 10.0);
			}
		}

		if(Math.abs(yv - ly) > 0.01)
		{
			if(ly > yv)
			{
				ly -= ((double) Math.abs(yv - ly) / 10.0);
			}

			if(ly < yv)
			{
				ly += ((double) Math.abs(yv - ly) / 10.0);
			}
		}

		frame.drawText(pixX - ReactFont.Font.getWidth(formatter.from(value)) - 6, getHeightForText(frame) - 1, ReactFont.Font, dt, formatter.from(value));
		frame.drawText(pixX - ReactFont.Font.getWidth(formatter.from(value)) - 5, getHeightForText(frame), ReactFont.Font, textColor, formatter.from(value));
		frame.drawText(3, 3, ReactFont.Font, dt, formatter.from(getMax()));
		frame.drawText(4, 4, ReactFont.Font, textColor, formatter.from(getMax()));
		frame.drawText(3, frame.getHeight() - 9, ReactFont.Font, dt, getName());
		frame.drawText(4, frame.getHeight() - 10, ReactFont.Font, textColor, getName());

		for(int i = 0; i < frame.getWidth(); i++)
		{
			for(int j = 0; j < frame.getHeight(); j++)
			{
				if((i == 0 || j == 0) || (i == frame.getWidth() - 1 || j == frame.getHeight() - 1))
				{
					frame.write(i, j, borderColor);
				}
			}
		}

		last = frame;
	}

	public int getHeightForText(BufferedFrame frame)
	{
		GList<Double> map = getPlotBoard().getBetween(M.ms() - (getTimeViewport() / 4), M.ms()).v();
		Average aa = new Average(map.size());

		for(Double i : map)
		{
			aa.put(i);
		}

		double dp = M.clip(1.0 - (aa.getAverage() / getMax()), 0, 1) * frame.getHeight();

		if(dp > frame.getHeight() / 2)
		{
			dp -= 24;
		}

		else
		{
			dp += 24;
		}

		if(Math.abs(dp - nextPoint) > 0.1)
		{
			if(dp < nextPoint)
			{
				nextPoint -= (Math.abs(dp - nextPoint) / 10.0);
			}

			else
			{
				nextPoint += (Math.abs(dp - nextPoint) / 10.0);
			}
		}

		return (int) nextPoint;
	}

	public int getHue(int red, int green, int blue)
	{

		float min = Math.min(Math.min(red, green), blue);
		float max = Math.max(Math.max(red, green), blue);
		float hue = 0f;

		if(max == red)
		{
			hue = (green - blue) / (max - min);

		}

		else if(max == green)
		{
			hue = 2f + (blue - red) / (max - min);

		}

		else
		{
			hue = 4f + (red - green) / (max - min);
		}

		hue = hue * 60;

		if(hue < 0)
		{
			hue = hue + 360;
		}

		return Math.round(hue);
	}

	public void scaleFor(GList<Double> nums)
	{
		double max = Double.MIN_VALUE;

		for(double i : nums)
		{
			if(i > max)
			{
				max = i;
			}
		}

		if(max < 0)
		{
			setMax(1.0);
			return;
		}

		if(Math.abs(getMax() - max) > 0.01)
		{
			double dist = Math.abs(getMax() - max);
			double move = dist / 5;
			setMax(getMax() > max ? getMax() - move : getMax() + move);
		}
	}

	public IFormatter getFormatter()
	{
		return formatter;
	}

	public void setFormatter(IFormatter formatter)
	{
		this.formatter = formatter;
	}

	public byte getGraphColor()
	{
		return graphColor;
	}

	public void setGraphColor(byte graphColor)
	{
		this.graphColor = graphColor;
	}

	public byte getBackgroundColor()
	{
		return backgroundColor;
	}

	public void setBackgroundColor(byte backgroundColor)
	{
		this.backgroundColor = backgroundColor;
	}

	public byte getBorderColor()
	{
		return borderColor;
	}

	public void setBorderColor(byte borderColor)
	{
		this.borderColor = borderColor;
	}

	public byte getTextColor()
	{
		return textColor;
	}

	public void setTextColor(byte textColor)
	{
		this.textColor = textColor;
	}

	public ISampler getSampler()
	{
		return sampler;
	}
}
