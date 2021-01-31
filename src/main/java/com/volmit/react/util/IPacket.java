package com.volmit.react.util;

public interface IPacket extends Streamable {
    int getId();

    PacketBinding getBinding();

    String getPacketName();
}
