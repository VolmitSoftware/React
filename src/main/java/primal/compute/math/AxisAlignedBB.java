package primal.compute.math;

public class AxisAlignedBB
{
	private double xa;
	private double xb;
	private double ya;
	private double yb;
	private double za;
	private double zb;

	public AxisAlignedBB(double xa, double xb, double ya, double yb, double za, double zb)
	{
		this.xa = xa;
		this.xb = xb;
		this.ya = ya;
		this.yb = yb;
		this.za = za;
		this.zb = zb;
	}

	public AxisAlignedBB(AlignedPoint a, AlignedPoint b)
	{
		this(a.getX(), b.getX(), a.getY(), b.getY(), a.getZ(), b.getZ());
	}

	public boolean contains(AlignedPoint p)
	{
		return p.getX() >= xa && p.getX() <= xb && p.getY() >= ya && p.getZ() <= yb && p.getZ() >= za && p.getZ() <= zb;
	}

	public boolean intersects(AxisAlignedBB s)
	{
		return this.xb >= s.xa && this.yb >= s.ya && this.zb >= s.za && s.xb >= this.xa && s.yb >= this.ya && s.zb >= this.za;
	}
}
