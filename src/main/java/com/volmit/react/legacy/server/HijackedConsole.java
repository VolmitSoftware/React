package com.volmit.react.legacy.server;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.bukkit.ChatColor;
import primal.lang.collection.GList;

@Plugin(name = "HijackedConsole", category = "Core", elementType = "appender", printObject = true)
public class HijackedConsole extends AbstractAppender {

    public static GList<String> out = new GList<>();

    public HijackedConsole() {
        super("HijackedConsole", null,
                PatternLayout.newBuilder().withPattern("[%d{HH:mm:ss} %level]: %msg").build());
    }

    @Override
    public boolean isStarted() {
        return true;
    }

    @Override
    public void append(LogEvent e) {
        if (e.getMessage().getFormattedMessage().length() > 0) {
            out.add(ChatColor.stripColor(e.getMessage().getFormattedMessage()));
        }
    }
}