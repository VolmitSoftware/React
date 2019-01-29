package com.volmit.react.api;

public abstract class NormalGraph implements IGraph
{
	private String name;
	private PlotBoard plotBoard;
	private long timeViewport;
	private double max;

	public NormalGraph(String name, long timeViewport)
	{
		this.name = name;
		this.timeViewport = timeViewport;
		plotBoard = new PlotBoard();
		max = 0;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public PlotBoard getPlotBoard()
	{
		return plotBoard;
	}

	@Override
	public long getTimeViewport()
	{
		return timeViewport;
	}

	@Override
	public void render(BufferedFrame frame)
	{
		onRender(frame);
	}

	@Override
	public double getMax()
	{
		return max;
	}

	@Override
	public void setMax(double max)
	{
		this.max = max;
	}

	public abstract void onRender(BufferedFrame frame);
}
