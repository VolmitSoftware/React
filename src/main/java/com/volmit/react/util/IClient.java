package com.volmit.react.util;

import java.io.IOException;

public interface IClient
{
	public int getPort();

	public String getAddress();

	public IPacket sendPacket(IPacket send) throws IOException, Exception;

	public PacketHandler getHandler();
}
