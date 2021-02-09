package com.volmit.react.util;

import java.io.IOException;

public interface IClient
{
	int getPort();

	String getAddress();

	IPacket sendPacket(IPacket send) throws IOException, Exception;

	PacketHandler getHandler();
}
