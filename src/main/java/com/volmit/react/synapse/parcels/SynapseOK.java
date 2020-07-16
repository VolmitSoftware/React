package com.volmit.react.synapse.parcels;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import ninja.bytecode.shuriken.web.Parcel;
import ninja.bytecode.shuriken.web.ParcelDescription;
import ninja.bytecode.shuriken.web.ParcelResponse;
import ninja.bytecode.shuriken.web.Parcelable;

@ToString
@EqualsAndHashCode(callSuper = false)
@ParcelResponse("Synapse")
@ParcelDescription("This is a server response usually to another request's success.")
public class SynapseOK extends Parcel
{
	private static final long serialVersionUID = -6806767374147741101L;

	public SynapseOK()
	{
		super("ok");
	}

	@Override
	public Parcelable respond()
	{
		return null;
	}
}
