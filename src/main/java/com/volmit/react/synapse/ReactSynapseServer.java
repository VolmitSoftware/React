package com.volmit.react.synapse;

import com.volmit.react.Config;
import com.volmit.react.React;

import ninja.bytecode.shuriken.logging.L;
import ninja.bytecode.shuriken.web.ParcelWebServer;

public class ReactSynapseServer
{
	private ParcelWebServer server;

	public ReactSynapseServer()
	{

	}

	public void start()
	{
		L.i("Starting Web Server");
		try
		{
			//@builder
			server = new ParcelWebServer()
				.configure()
					.http(false)
					.https(true)
					.httpPort(80)
					.httpsPort(Config.SYNAPSE_PORT)
					.maxFormContentSize(1024*1024*1024)
					.serverPath(Config.SYNAPSE_PATH)
//					.sslKeystore(k.getString("keystore"))
//					.sslKeystorePassword(k.getString("keystorePassword"))
//					.sslKeystoreKeyName(k.getString("key"))
//					.sslKeystoreKeyPassword(k.getString("keyPassword"))
				.applySettings()
				.addParcelables(React.class, "com.volmit.react.synapse.parcels")
			.start();
			//@done
		}

		catch(Throwable e)
		{
			L.f("Failed to start synapse webserver. Portbind?");
			L.ex(e);
		}
	}

	public void stop()
	{
		if(server == null)
		{
			return;
		}

		try
		{
			server.stop();
		}

		catch(Throwable e)
		{

		}
	}
}
