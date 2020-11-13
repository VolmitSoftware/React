package com.volmit.react.util;

public class NMSClass
{

	private static boolean init = false;
	protected static int version = 170;

	public static Class<?> PacketPlayOutPlayerListHeaderFooter;
	public static Class<?> IChatBaseComponent;
	public static Class<?> ChatSerializer;
	public static Class<?> PacketPlayOutPlayerInfo;
	public static Class<?> PlayerInfoData;
	protected static Class<?> EnumPlayerInfoAction;
	public static Class<?> GameProfile;
	public static Class<?> EnumGamemode;
	public static Class<?> TileEntitySkull;
	public static Class<?> LoadingCache;

	static
	{
		if(!init)
		{
			if(NMSX.getVersion().contains("1_7"))
			{
				version = 170;
			}

			if(NMSX.getVersion().contains("1_8"))
			{
				version = 180;
			}

			if(NMSX.getVersion().contains("1_8_R1"))
			{
				version = 181;
			}

			if(NMSX.getVersion().contains("1_8_R2"))
			{
				version = 182;
			}

			if(NMSX.getVersion().contains("1_8_R3"))
			{
				version = 183;
			}

			if(version >= 180)
			{
				PacketPlayOutPlayerListHeaderFooter = NMSX.getNMSClass("PacketPlayOutPlayerListHeaderFooter");
			}

			IChatBaseComponent = NMSX.getCBNMSClass("IChatBaseComponent");

			if(version < 181)
			{
				ChatSerializer = NMSX.getCBNMSClass("ChatSerializer");
			}
			else
			{
				ChatSerializer = NMSX.getCBNMSClass("IChatBaseComponent$ChatSerializer");
			}

			PacketPlayOutPlayerInfo = NMSX.getCBNMSClass("PacketPlayOutPlayerInfo");

			if(version >= 180)
			{
				PlayerInfoData = NMSX.getCBNMSClass("PacketPlayOutPlayerInfo$PlayerInfoData");
			}

			if(version <= 181)
			{
				EnumPlayerInfoAction = NMSX.getCBNMSClass("EnumPlayerInfoAction");
			}

			else
			{
				EnumPlayerInfoAction = NMSX.getCBNMSClass("PacketPlayOutPlayerInfo$EnumPlayerInfoAction");
			}

			try
			{
				if(version < 180)
				{
					GameProfile = Class.forName("net.minecraft.util.com.mojang.authlib.GameProfile");
				}

				else
				{
					GameProfile = Class.forName("com.mojang.authlib.GameProfile");
				}
			}

			catch(Throwable e)
			{

			}

			if(version < 182)
			{
				EnumGamemode = NMSX.getCBNMSClass("EnumGamemode");
			}

			else
			{
				EnumGamemode = NMSX.getCBNMSClass("WorldSettings$EnumGamemode");
			}

			TileEntitySkull = NMSX.getCBNMSClass("TileEntitySkull");

			try
			{
				if(version < 180)
				{
					LoadingCache = Class.forName("net.minecraft.util.com.google.common.cache.LoadingCache");
				}
				else
				{
					LoadingCache = Class.forName("com.google.common.cache.LoadingCache");
				}
			}

			catch(Throwable e)
			{

			}

			init = true;
		}
	}

}