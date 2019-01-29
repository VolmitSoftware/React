package com.volmit.react.command;

import java.io.File;
import java.net.URL;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import com.volmit.react.Gate;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SideGate;
import com.volmit.react.util.A;
import com.volmit.react.util.C;
import com.volmit.react.util.Download;
import com.volmit.react.util.DownloadMonitor;
import com.volmit.react.util.DownloadState;
import com.volmit.react.util.F;
import com.volmit.react.util.S;
import com.volmit.volume.lang.collections.GList;

public class CommandInstallAgent extends ReactCommand
{
	public CommandInstallAgent()
	{
		command = "installagent";
		aliases = new String[] {"updateagent"};
		permissions = new String[] {Permissable.ACCESS.getNode(), Permissable.SYSTEMINFO.getNode(), Permissable.RELOAD.getNode()};
		usage = "";
		description = "Downloads & Updates the Memory Agent";
		sideGate = SideGate.ANYTHING;
	}

	@Override
	public List<String> onTabComplete(CommandSender arg0, Command arg1, String arg2, String[] arg3)
	{
		GList<String> l = new GList<String>();

		return l;
	}

	@Override
	public void fire(CommandSender sender, String[] args)
	{
		doit(sender);
	}

	public static void doit(CommandSender sender)
	{
		if(Permissable.ACCESS.has(sender))
		{
			Gate.msgActing(sender, "Downloading Volume Memory Agent.");

			new A()
			{
				@Override
				public void run()
				{
					try
					{
						URL u = new URL("http://nexus.volmit.com/service/local/repositories/volmit/content/com/javamex/classmexer/ClassMexer/1.0/ClassMexer-1.0.jar");
						Download d = new Download(new DownloadMonitor()
						{
							@Override
							public void onDownloadUpdateProgress(Download download, long bytes, long totalBytes, double percentComplete)
							{
								new S("")
								{
									@Override
									public void run()
									{
										Gate.msgActing(sender, "Downloading: " + C.WHITE + F.pc(percentComplete));
									}
								};
							}

							@Override
							public void onDownloadStateChanged(Download download, DownloadState from, DownloadState to)
							{

							}

							@Override
							public void onDownloadStarted(Download download)
							{

							}

							@Override
							public void onDownloadFinished(Download download)
							{
								new S("")
								{
									@Override
									public void run()
									{
										Gate.msgSuccess(sender, "Download Complete!");
									}
								};
							}

							@Override
							public void onDownloadFailed(Download download)
							{
								new S("")
								{
									@Override
									public void run()
									{
										Gate.msgError(sender, "Download FAILED!");
									}
								};
							}
						}, u, new File("classmexer.jar"), 512);

						d.start();
					}

					catch(Exception e)
					{

					}
				}
			};
		}
	}
}
