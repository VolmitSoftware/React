package com.volmit.react.legacy.server;

import com.volmit.react.util.DataCluster;
import org.bukkit.ChatColor;
import primal.lang.collection.GList;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class HijackedConsole extends Thread {
    public static boolean hijacked = false;
    public static GList<String> out = new GList<>();
    private final DataCluster cc;

    public HijackedConsole(DataCluster cc) {
        this.cc = cc;
    }

    @Override
    public void run() {
        if (!cc.getBoolean("hijack-console")) {
            return;
        }

        while (hijacked) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            PrintStream old = System.out;
            System.setOut(ps);

            int ms = cc.getInt("console-hacks.update-interval");

            if (ms < 50) {
                ms = 50;
            }

            if (ms > 4000) {
                ms = 4000;
            }

            try {
                Thread.sleep(ms);
            } catch (InterruptedException ignored) {

            }

            System.out.flush();
            System.setOut(old);

            if (baos.toString().length() > 0) {
                for (String i : baos.toString().split("\n")) {

                    if (!cc.getBoolean("console-hacks.color")) {
                        System.out.println(ChatColor.stripColor(i.replace("[0;36;1m", "").replace("[0;30;22m", "").replace("[0;34;22m", "").replace("[0;32;22m", "").replace("[0;36;22m", "").replace("[0;31;22m", "").replace("[0;35;22m", "").replace("[0;37;22m", "").replace("[0;34;1m", "").replace("[5m", "").replace("[21m", "").replace("[9m", "").replace("[4m", "").replace("[3m", "").replace("[0;33;22m", "").replace("[0;31;1m", "").replace("[0;35;1m", "").replace("[0;32;1m", "").replace("[0;33;22m", "").replace("[0;33;1m", "").replace("[0;37;1m", "").replace("[0;30;1m", "").replace("[0;30;1m", "").replace("[m", "").replace("", "").replace("[m", "")));
                    } else {
                        System.out.println(i);
                    }

                    String f = i.trim();

                    out.add(f);
                }

                while (out.size() > 256) {
                    out.pop();
                }
            }
        }
    }
}
