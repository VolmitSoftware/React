package primal.bukkit.nms;

public class Catalyst
{
	public static final CatalystHost host = getHost();

	private static CatalystHost getHost()
	{
		NMSVersion v = NMSVersion.current();
	
		switch(v)
		{
			case R1_10:
				return new Catalyst10();
			case R1_11:
				return new Catalyst11();
			case R1_12:
				return new Catalyst12();
			case R1_13_1:
				return new Catalyst13_R2();
			case R1_14:
				return new Catalyst14();
			case R1_15:
				return new Catalyst15();
			case R1_16:
				return new Catalyst16();
			case R1_8:
				return new Catalyst8();
			case R1_9_4:
				return new Catalyst94();
			default:
				return null;
		}
	}
}
