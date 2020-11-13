package primal.bukkit.nms;

import org.bukkit.Location;

public class BlockChange
{
	private int pos;
	private int y;
	private int id;
	private byte data;

	public BlockChange(Location l, int id, byte data)
	{
		this.pos = l.getBlockX() << 12 | l.getBlockZ() << 8 | y;
		this.id = id;
		this.data = data;
	}

	public BlockChange(int pos, int y, int id, byte data)
	{
		this.pos = pos;
		this.y = y;
		this.id = id;
		this.data = data;
	}

	public int getPos()
	{
		return pos;
	}

	public void setPos(int pos)
	{
		this.pos = pos;
	}

	public int getY()
	{
		return y;
	}

	public void setY(int y)
	{
		this.y = y;
	}

	public int getId()
	{
		return id;
	}

	public void setId(int id)
	{
		this.id = id;
	}

	public byte getData()
	{
		return data;
	}

	public void setData(byte data)
	{
		this.data = data;
	}
}
