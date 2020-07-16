package com.volmit.react.synapse.parcels;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import ninja.bytecode.shuriken.web.Parcel;
import ninja.bytecode.shuriken.web.ParcelDescription;
import ninja.bytecode.shuriken.web.ParcelResponse;
import ninja.bytecode.shuriken.web.Parcelable;

@ToString
@EqualsAndHashCode(callSuper = false)
@ParcelResponse("Synapse")
@ParcelDescription("This is a server response usually to another request's error.")
public class SynapseError extends Parcel
{
	private static final long serialVersionUID = -6806767374147741101L;

	@Getter
	@Setter
	@ParcelDescription("A human readable description of the error. Useful & Safe to show in-app")
	private String message;

	public SynapseError(String message)
	{
		super("error");
		this.message = message;
	}

	public int getStatusHTTPCode()
	{
		return 500;
	}

	public SynapseError()
	{
		this("Unexpected Error");
	}

	@Override
	public Parcelable respond()
	{
		return null;
	}
}
