package com.volmit.react.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Client implements IClient
{
	private int port;
	private String address;
	private PacketHandler handler;
	private Socket socket;

	public Client(String address, int port)
	{
		this.port = port;
		this.address = address;
		this.handler = new PacketHandler(PacketBinding.CLIENT_BOUND, null, null);
	}

	private void connect() throws IOException
	{
		socket = new Socket();
		socket.setTrafficClass(0x10);
		socket.setTcpNoDelay(true);
		socket.setSoTimeout(10000);
		socket.setPerformancePreferences(1, 2, 0);
		socket.connect(new InetSocketAddress(address, port), 1000);
	}

	private void disconnect() throws IOException
	{
		socket.close();
	}

	@Override
	public int getPort()
	{
		return port;
	}

	@Override
	public String getAddress()
	{
		return address;
	}

	@Override
	public IPacket sendPacket(IPacket send) throws Exception
	{
		connect();
		System.out.println("-> " + send.getPacketName());
		ISS in = new ISS(socket.getInputStream());
		OSS out = new OSS(socket.getOutputStream());
		handler.redirect(out, in);
		handler.write(send);
		out.flush();
		IPacket response = handler.read();
		System.out.println("<- " + response.getPacketName());
		disconnect();

		return response;
	}

	@Override
	public PacketHandler getHandler()
	{
		return handler;
	}
}
