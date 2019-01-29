package com.volmit.react.util;

public interface IPacket extends Streamable
{
	public int getId();

	public PacketBinding getBinding();

	public String getPacketName();
}
