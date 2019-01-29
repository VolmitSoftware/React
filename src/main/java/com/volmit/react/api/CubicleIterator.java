package com.volmit.react.api;

import com.volmit.react.util.D;

public class CubicleIterator
{
	private int[][] mapping;
	private int xCubes = 2;
	private int yCubes = 64;

	public CubicleIterator()
	{
		mapping = new int[yCubes][xCubes];

		for(int i = 0; i < yCubes; i++)
		{
			for(int j = 0; j < xCubes; j++)
			{
				mapping[i][j] = -1;
			}
		}
	}

	public Point positionFor(int id)
	{
		if(id == -1)
		{
			return null;
		}

		for(int i = 0; i < yCubes; i++)
		{
			for(int j = 0; j < xCubes; j++)
			{
				if(mapping[i][j] == id)
				{
					return new Point(j * 64, i * 64);
				}
			}
		}

		return null;
	}

	public void insert(GraphSize s, int gid)
	{
		if(s.equals(GraphSize.FULL))
		{
			int at = -1;
			int requiredFor = xCubes;

			for(int i = 0; i < yCubes; i++)
			{
				boolean succ = true;

				for(int j = 0; j < xCubes; j++)
				{
					if(mapping[i][j] != -1)
					{
						succ = false;
						break;
					}
				}

				if(succ)
				{
					requiredFor--;

					if(requiredFor <= 0)
					{
						at = i - (xCubes - 1);
						break;
					}
				}
			}

			if(at != -1)
			{
				for(int i = at; i < xCubes + at; i++)
				{
					for(int j = 0; j < xCubes; j++)
					{
						mapping[i][j] = gid;
					}
				}
			}
		}

		if(s.equals(GraphSize.WIDE))
		{
			for(int i = 0; i < yCubes; i++)
			{
				boolean succ = true;

				for(int j = 0; j < xCubes; j++)
				{
					if(mapping[i][j] != -1)
					{
						succ = false;
						break;
					}
				}

				if(succ)
				{
					for(int j = 0; j < xCubes; j++)
					{
						mapping[i][j] = gid;
					}

					break;
				}
			}
		}

		if(s.equals(GraphSize.SQUARE))
		{
			search: for(int i = 0; i < yCubes; i++)
			{
				for(int j = 0; j < xCubes; j++)
				{
					if(mapping[i][j] == -1)
					{
						mapping[i][j] = gid;
						break search;
					}
				}
			}
		}
	}

	public void print()
	{
		for(int i = 0; i < yCubes; i++)
		{
			String k = "";

			for(int j = 0; j < xCubes; j++)
			{
				k += "[" + positionFor(mapping[i][j]) + "] ";
			}

			D.v(k);
		}
	}
}
