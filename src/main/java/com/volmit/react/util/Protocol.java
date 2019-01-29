package com.volmit.react.util;

import org.bukkit.Bukkit;

public enum Protocol
{
	LATEST(10000, "Latest"),
	R1_13_1(393, "1.13.1", "v1_13_R1"),
	R1_13(393, "1.13", "v1_13_R1"),
	R1_12_2(340, "1.12.2", "v1_12_R1"),
	R1_12_2_PRE(339, "1.12.2-PRE", "v1_12_R1"),
	R1_12_1(338, "1.12.1", "v1_12_R1"),
	R1_12(335, "1.12", "v1_12_R1"),
	R1_11_2(316, "1.11.2", "v1_11_R1"),
	R1_11_1(316, "1.11.1", "v1_11_R1"),
	R1_11(315, "1.11", "v1_11_R1"),
	R1_10_2(210, "1.10.2", "v1_10_R1"),
	R1_10_1(210, "1.10.1", "v1_10_R1"),
	R1_10(210, "1.10", "v1_10_R1"),
	R1_9_4(110, "1.9.4", "v1_9_R2"),
	R1_9_3(110, "1.9.3", "v1_9_R2"),
	R1_9_2(109, "1.9.2", "v1_9_R1"),
	R1_9_1(108, "1.9.1", "v1_9_R1"),
	R1_9(107, "1.9", "v1_9_R1"),
	R1_8_9(47, "1.8.9", "v1_8_R3"),
	R1_8_8(47, "1.8.8", "v1_8_R3"),
	R1_8_7(47, "1.8.7", "v1_8_R3"),
	R1_8_6(47, "1.8.6", "v1_8_R3"),
	R1_8_5(47, "1.8.5", "v1_8_R3"),
	R1_8_4(47, "1.8.4", "v1_8_R3"),
	R1_8_3(47, "1.8.3", "v1_8_R3"),
	R1_8_2(47, "1.8.2", "v1_8_R3"),
	R1_8_1(47, "1.8.1", "v1_8_R3"),
	R1_8(47, "1.8", "v1_8_R3"),
	R1_7_10(5, "1.7.10"),
	R1_7_9(5, "1.7.9"),
	R1_7_8(5, "1.7.8"),
	R1_7_7(5, "1.7.7"),
	R1_7_6(5, "1.7.6"),
	R1_7_5(4, "1.7.5"),
	R1_7_4(4, "1.7.4"),
	R1_7_3(4, "1.7.3"),
	R1_7_2(4, "1.7.2"),
	R1_7_1(4, "1.7.1"),
	B1_6_4(78, "1.6.4", true),
	B1_6_3(77, "1.6.3", true),
	B1_6_2(74, "1.6.2", true),
	B1_6_1(73, "1.6.1", true),
	B1_5_2(61, "1.5.2", true),
	B1_5_1(60, "1.5.1", true),
	B1_5(60, "1.5", true),
	B1_4_7(51, "1.4.7", true),
	B1_4_6(51, "1.4.6", true),
	B1_4_5(49, "1.4.5", true),
	B1_4_4(49, "1.4.4", true),
	B1_4_2(47, "1.4.2", true),
	B1_3_2(39, "1.3.2", true),
	B1_3_1(39, "1.3.1", true),
	B1_2_5(29, "1.2.5", true),
	B1_2_4(29, "1.2.4", true),
	EARLIEST(0),
	UNKNOWN(-10000);

	private static Protocol CURRENT = null;
	private int version;
	private String packageVersion;
	private String versionName;
	private boolean netty;

	private Protocol(int version, String versionName, boolean beta)
	{
		this(version, versionName, "UNKNOWN", beta);
	}

	private Protocol(int version)
	{
		this(version, "UNKNOWN", "UNKNOWN", false);
	}

	private Protocol(int version, String versionName)
	{
		this(version, versionName, "UNKNOWN", false);
	}

	private Protocol(int version, String versionName, String packageVersion)
	{
		this(version, versionName, packageVersion, false);
	}

	private Protocol(int version, String versionName, String packageVersion, boolean beta)
	{
		this.version = version;
		this.versionName = versionName;
		this.packageVersion = packageVersion;
		netty = beta;

		if(beta)
		{
			version -= 1000;
		}
	}

	public boolean hasPackageSupport()
	{
		try
		{
			Class.forName("net.minecraft.server." + packageVersion + ".Block");
			return true;
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}

		return false;
	}

	public String getPackageVersion()
	{
		return packageVersion;
	}

	@Override
	public String toString()
	{
		return versionName;
	}

	public static Protocol getSupportedNMSVersion()
	{
		for(Protocol i : values())
		{
			if(i.isActualVersion() && i.isServerVersion() && i.hasPackageSupport())
			{
				return i;
			}
		}

		return null;
	}

	public static Protocol getProtocolVersion()
	{
		if(CURRENT == null)
		{
			for(Protocol i : values())
			{
				if(i.isServerVersion())
				{
					CURRENT = i;
					return i;
				}
			}

			CURRENT = Protocol.UNKNOWN;
		}

		return CURRENT;
	}

	public ProtocolRange to(Protocol p)
	{
		return new ProtocolRange(this, p);
	}

	public boolean isServerVersion()
	{
		return Bukkit.getBukkitVersion().startsWith(getVersionString());
	}

	public String getVersionString()
	{
		return toString();
	}

	public boolean isNettySupported()
	{
		return !netty;
	}

	public boolean isActualVersion()
	{
		return toString().contains(".");
	}

	public int getVersion()
	{
		if(isActualVersion() && !isNettySupported())
		{
			return getMetaVersion() + 1000;
		}

		return getMetaVersion();
	}

	public int getCVersion()
	{
		if(isActualVersion() && !isNettySupported())
		{
			return getMetaVersion();
		}

		return getMetaVersion() + 1000;
	}

	public int getMetaVersion()
	{
		return version;
	}
}
