package com.volmit.react.command;

import com.volmit.react.Gate;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactCommand;
import com.volmit.react.api.SideGate;
import com.volmit.react.util.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import primal.lang.collection.GList;
import primal.util.text.C;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

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

    public static void doit(CommandSender sender) {
        if (Permissable.ACCESS.has(sender)) {
            Gate.msgActing(sender, "Downloading Volume Memory Agent.");


            new A() {
                @Override
                public void run() {
                    try {
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
                                        new S("") {
                                            @Override
                                            public void run() {
                                                Gate.msgActing(sender, "Cleaning up files..");
                                            }
                                        };

                                        if (new File("README.txt").delete() && new File("classmexer.zip").delete()) {
                                            new S("") {
                                                @Override
                                                public void run() {
                                                    Gate.msgSuccess(sender, "Successfully cleaned up files.");
                                                    Gate.msgActing(sender, "Please follow the rest of the documentation to install the memory agent.");
                                                }
                                            };
                                        } else {
                                            new S("") {
                                                @Override
                                                public void run() {
                                                    Gate.msgError(sender, "There was an error cleaning up files.");
                                                }
                                            };
                                        }
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
                        }, new File(System.getProperty("user.dir"), "classmexer.zip"), new File(System.getProperty("user.dir")), 512);
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
                                        try {
                                            unzip.start();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
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

                    } catch (Exception e) {
                        e.printStackTrace();
                    }


                }
            };
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender arg0, Command arg1, String arg2, String[] arg3) {

        return new GList<>();
    }

    @Override
    public void fire(CommandSender sender, String[] args) {
        doit(sender);
    }
}
