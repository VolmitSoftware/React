package com.volmit.react.command;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.volmit.react.util.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import com.volmit.react.Gate;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SideGate;

import primal.lang.collection.GList;
import primal.util.text.C;

public class CommandInstallAgent extends ReactCommand {

	private final static String library = "https://www.javamex.com/classmexer/classmexer-0_03.zip";

	public CommandInstallAgent() {
		command = "installagent";
		aliases = new String[]{"updateagent"};
		permissions = new String[]{Permissable.ACCESS.getNode(), Permissable.SYSTEMINFO.getNode(), Permissable.RELOAD.getNode()};
		usage = "";
		description = "Downloads & Updates the Memory Agent";
		sideGate = SideGate.ANYTHING;
	}

	@Override
	public List<String> onTabComplete(CommandSender arg0, Command arg1, String arg2, String[] arg3) {
		GList<String> l = new GList<String>();

		return l;
	}

	@Override
	public void fire(CommandSender sender, String[] args) {
		doit(sender);
	}

	public static void doit(CommandSender sender) {
		if (Permissable.ACCESS.has(sender)) {
			Gate.msgActing(sender, "Downloading Volume Memory Agent.");

			new A() {
				@Override
				public void run() {
					try {
						URL u = new URL(library);
						Download d = new Download(new DownloadMonitor() {
							@Override
							public void onDownloadUpdateProgress(Download download, long bytes, long totalBytes, double percentComplete) {
								new S("") {
									@Override
									public void run() {
										Gate.msgActing(sender, "Downloading: " + C.WHITE + F.pc(percentComplete));
									}
								};
							}

							@Override
							public void onDownloadStateChanged(Download download, DownloadState from, DownloadState to) {

							}

							@Override
							public void onDownloadStarted(Download download) {

							}

							@Override
							public void onDownloadFinished(Download download) {
								new S("") {
									@Override
									public void run() {
										Gate.msgSuccess(sender, "Download Complete!");
									}
								};
							}

							@Override
							public void onDownloadFailed(Download download) {
								new S("") {
									@Override
									public void run() {
										Gate.msgError(sender, "Download FAILED!");
									}
								};
							}
						}, u, new File("classmexer.zip"), 512);
						d.start();

						Unzip unzip = new Unzip(new UnzipMonitor() {
							@Override
							public void onUnzipUpdateProgress(Unzip unzip, long bytes, long totalBytes, double percentComplete) {
								new S("") {
									@Override
									public void run() {
										Gate.msgActing(sender, "Unzipping: " + C.WHITE + F.pc(percentComplete));
									}
								};
							}

							@Override
							public void onUnzipStateChanged(Unzip unzip, UnzipState from, UnzipState to) {

							}

							@Override
							public void onUnzipStarted(Unzip unzip) {

							}

							@Override
							public void onUnzipFinished(Unzip unzip) {
								new S("") {
									@Override
									public void run() {
										Gate.msgSuccess(sender, "Unzip Complete!");
									}
								};
							}

							@Override
							public void onUnzipFailed(Unzip unzip) {
								new S("") {
									@Override
									public void run() {
										Gate.msgError(sender, "Unzip FAILED!");
									}
								};
							}
						}, new File("classmexer.zip"), new File("/"), 512);
						unzip.start();


/*
						byte[] buffer = new byte[1024];
						FileInputStream fis = new FileInputStream("classmexer.zip");
						ZipInputStream zis = new ZipInputStream(fis);
						ZipEntry zipEntry = zis.getNextEntry();
						String fileName = zipEntry.getName();
						while (zipEntry != null && fileName == "classmexer.jar") {
							File newFile = new File(fileName);
							System.out.println("Unzipping to " + newFile.getAbsolutePath());
							FileOutputStream fos = new FileOutputStream(newFile);
							int len;
							while ((len = zis.read(buffer)) > 0) {
								fos.write(buffer, 0, len);
							}
							fos.close();
							//close this ZipEntry
							zis.closeEntry();
							zipEntry = zis.getNextEntry();
						}
						//close last ZipEntry
						zis.closeEntry();
						zis.close();
*/
						new S("") {
							@Override
							public void run() {
								Gate.msgActing(sender, "Cleaning up files..");
							}
						};

						File file = new File("classmexer.zip");
						if(file.delete())
						{
							new S("") {
								@Override
								public void run() {
									Gate.msgSuccess(sender, "Successfully cleaned up files.");
								}
							};
						}
						else
						{
							new S("") {
								@Override
								public void run() {
									Gate.msgError(sender, "There was an error cleaning up files.");
								}
							};
						}

					} catch (Exception e) {
						e.printStackTrace();
					}


				}

				;
			};
		}
	}
}
