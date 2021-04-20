package com.volmit.react.util;

public interface IServer {
    int getPort();

    PacketHandler getHandler();

    IPacket onPacketReceived(IPacket p);
}
