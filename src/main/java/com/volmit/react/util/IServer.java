package com.volmit.react.util;

public interface IServer
{
	public int getPort();

	public PacketHandler getHandler();

	public IPacket onPacketReceived(IPacket p);
}
