package com.volmit.react.api.monitor;

import com.volmit.react.React;
import com.volmit.react.api.monitor.configuration.MonitorConfiguration;
import com.volmit.react.api.monitor.configuration.MonitorGroup;
import com.volmit.react.api.player.ReactPlayer;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.util.C;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

public class ActionBarMonitor extends PlayerMonitor {
    private MonitorConfiguration configuration;
    private MonitorGroup focus;
    private int focusUpAnimation = 0;
    private boolean focusDownAnimation = false;

    public ActionBarMonitor(ReactPlayer player) {
        super("actionbar", player, 50);
        sleepingRate = 1000;
    }

    public ActionBarMonitor sample(MonitorConfiguration configuration) {
        this.configuration = configuration;

        return this;
    }

    public boolean isAlwaysFlushing(){
        return true;
    }

    @Override
    public void stop() {
        getPlayer().getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(" "));
        super.stop();
    }

    private String writeHeaderTitle(MonitorGroup group) {
        return (focusUpAnimation > 2 ? group.getColor().toString() : C.DARK_GRAY.toString()) + (focusUpAnimation > 3 ? C.BOLD : "") + group.getName();
    }

    private String writeSubSamplers(MonitorGroup group) {
        StringBuilder b = new StringBuilder();

        for(Sampler i : group.getSubSamplers()) {
            b.append(" ").append(group.getColor().toString())
                .append(getSamplers().get(i) == null ? "..." : i.format(getSamplers().get(i)));
        }

        return b.substring(1);
    }

    private String writeHeader()
    {
        StringBuilder b = new StringBuilder();

        for(MonitorGroup i : configuration.getGroups())
        {
            Sampler head = i.getHeadSampler();
            b.append(C.RESET).append(" ")
                .append(i.getColor())
                .append((i == focus ? C.UNDERLINE : ""))
                .append(getSamplers().get(head) == null ? "..." : head.format(getSamplers().get(head)));
        }

        return b.substring(3);
    }

    @Override
    public void flush() {
        clearVisibility();

        MonitorGroup f = focus;
        focus = getPlayer().isSneaking() ? configuration.getGroups().get(getPlayer().getScrollPosition(configuration.getGroups().size())) : null;

        if(focus != f) {
            if(focus != null) {
                focusUpAnimation = 4;
            }

            else {
                focusDownAnimation = true;
            }
        }

        for(MonitorGroup i : configuration.getGroups()) {
            setVisible(i.getHeadSampler(), true);

            if(focus == i) {
                setVisible(i.getSubSamplers(), true);
            }
        }


        if(focus != null) {
            String t = " ";

            if(focusUpAnimation > 0) {
                t = writeHeaderTitle(focus);
                focusUpAnimation--;
            }

            getPlayer().getPlayer().sendTitle(t, writeSubSamplers(focus), 0, (int) ((getInterval() / 50) + 3), 20);
        }

        else if(focusDownAnimation)
        {
            getPlayer().getPlayer().sendTitle(" ", "  ", 0, (int) ((getInterval() / 50) + 1), 17);
        }

        getPlayer().getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(writeHeader()));
    }
}
