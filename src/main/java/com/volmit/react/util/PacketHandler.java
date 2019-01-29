package com.volmit.react.util;

import com.volmit.volume.lang.collections.GList;

public class PacketHandler
{
	private GList<IPacket> accept;
	private PacketBinding side;
	private OSS out;
	private ISS in;

	public PacketHandler(PacketBinding side, OSS out, ISS in)
	{
		accept = new GList<IPacket>();
		redirect(out, in);
	}

	public void redirect(OSS out, ISS in)
	{
		this.in = in;
		this.out = out;
	}

	public void accept(IPacket packet) throws IncompatablePacketException
	{
		if(packet.getBinding().equals(side))
		{
			throw new IncompatablePacketException("Packet cannot send " + side + " to " + side);
		}

		accept.add(packet);
	}

	private IPacket findPacket(int id)
	{
		for(IPacket i : accept)
		{
			if(i.getId() == id)
			{
				return i;
			}
		}

		return null;
	}

	public IPacket read() throws Exception
	{
		int id = in.readInt();
		int length = in.readInt();
		byte[] pdata = new byte[length];
		in.readFully(pdata);
		IPacket packet = findPacket(id);
		ISS inv = new ISS(pdata);

		if(packet == null)
		{
			inv.close();
			throw new UnhandledPacketException("Unable to identify packet id: " + id);
		}

		packet.fromBytes(inv);
		inv.close();
		return packet;
	}

	public void write(IPacket packet) throws Exception
	{
		OSS buf = new OSS();
		packet.toBytes(buf);
		byte[] pdata = buf.getBytes();
		out.writeInt(packet.getId());
		out.writeInt(pdata.length);
		out.write(pdata);
	}
}
