package com.volmit.react.api.monitor;

import com.volmit.react.api.sampler.Sampler;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

public class ActionBarMonitor extends PlayerMonitor {
    public ActionBarMonitor(Player player) {
        super("actionbar", player, 50);
        sleepingRate = 1000;
    }

    public boolean isAlwaysFlushing(){
        return true;
    }

    @Override
    public void flush() {
        StringBuilder sb = new StringBuilder();

        for (Sampler i : getSamplers().keySet()) {
            sb.append(i.sampleFormatted()).append(" ");
        }

        getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(sb.toString()));
    }
}
