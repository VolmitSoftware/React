package com.volmit.react.api;

import com.volmit.react.ReactPlugin;
import com.volmit.react.util.M;
import primal.util.text.C;

import java.io.*;
import java.util.Date;

public class EventLog {
    private final File f;
    private PrintWriter pw;
    private BufferedReader bu;
    private String line;

    @SuppressWarnings("deprecation")
    public EventLog() throws FileNotFoundException {
        Date d = new Date(M.ms());
        f = new File(new File(ReactPlugin.i.getDataFolder(), "cache"), d.getMonth() + "-" + d.getDate() + "-" + (d.getYear() + 1900) + ".log");
        f.getParentFile().mkdirs();
    }

    public void openWrite() throws FileNotFoundException {
        pw = new PrintWriter(new FileOutputStream(f, true), true);
    }

    public void closeWrite() {
        pw.close();
    }

    public void openRead() throws FileNotFoundException {
        bu = new BufferedReader(new FileReader(f));
    }

    public void closeRead() throws IOException {
        bu.close();
    }

    public String readLine() throws IOException {
        line = bu.readLine();
        return line;
    }

    public void log(EventType t, String msg) {
        String lg = "[" + stamp() + "] " + t.name() + ": " + C.stripColor(msg);
        pw.println(lg);
    }

    @SuppressWarnings("deprecation")
    public String stamp() {
        Date d = new Date(M.ms());
        return d.getHours() + ":" + d.getMinutes() + ":" + d.getSeconds();
    }
}
