package com.volmit.react.util;

public interface IClient {
    int getPort();

    String getAddress();

    IPacket sendPacket(IPacket send) throws Exception;

    PacketHandler getHandler();
}
