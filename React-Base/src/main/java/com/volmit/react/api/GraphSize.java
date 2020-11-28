package com.volmit.react.api;

public enum GraphSize
{
	WIDE,
	FULL,
	SQUARE;

	public Point toPoint()
	{
		switch(this)
		{
			case FULL:
				return new Point(128, 128);
			case SQUARE:
				return new Point(64, 64);
			case WIDE:
				return new Point(128, 64);
		}

		return null;
	}
}
