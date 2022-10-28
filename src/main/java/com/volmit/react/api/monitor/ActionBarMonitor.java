package com.volmit.react.api.monitor;

import com.volmit.react.React;
import com.volmit.react.api.monitor.configuration.MonitorConfiguration;
import com.volmit.react.api.monitor.configuration.MonitorGroup;
import com.volmit.react.api.player.ReactPlayer;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.util.C;
import com.volmit.react.util.Form;
import com.volmit.react.util.M;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.Keyed;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Duration;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ActionBarMonitor extends PlayerMonitor {
    private MonitorConfiguration configuration;
    private MonitorGroup focus;
    private int focusUpAnimation = 0;
    private boolean focusDownAnimation = false;
    private boolean tickUp = false;

    public ActionBarMonitor(ReactPlayer player) {
        super("actionbar", player, 50);
        sleepDelay = 10;
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

    private Component writeHeaderTitle(MonitorGroup group) {
        if(focusUpAnimation > 3)
        {
            return Component.text(group.getName()).style(Style.style(TextColor.fromHexString(group.getColor()), TextDecoration.BOLD));
        }

        if(focusUpAnimation > 2)
        {
            return Component.text(group.getName()).style(Style.style(TextColor.fromHexString(darker(group.getColor()))));
        }

        if(focusUpAnimation > 1)
        {
            return Component.text(group.getName()).style(Style.style(TextColor.fromHexString(darker(darker(group.getColor())))).font(Key.key("uniform")));
        }

        return Component.text(group.getName()).style(Style.style(TextColor.fromHexString(darker(darker(darker(group.getColor()))))).font(Key.key("uniform")));
    }

    private String changify(String color, double change)
    {
        Color c = new Color(Integer.parseInt(color.substring(1), 16));
        float [] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);

        hsb[0] = (float) (M.lerp(hsb[0], hsb[0] + 0.05, change));
        hsb[1] = (float) (M.lerp(hsb[1] * 0.8, 1, change));
        hsb[2] = (float) (M.lerp(hsb[2] * 0.8, 1, change));

        return "#" + Integer.toString(Color.getHSBColor(hsb[0], hsb[1], hsb[2]).getRGB(), 16);
    }

    private String darker(String color)
    {
        return "#" + Integer.toString(new Color(Integer.parseInt(color.substring(1), 16)).darker().getRGB(), 16);
    }

    private Component writeSubSamplers(MonitorGroup group) {
        var builder = Component.text();

        boolean first = true;

        for(Sampler i : group.getSubSamplers()) {
            if(!first)
            {
                builder.append(Component.space());
            }

            Style s = Style.style(TextColor.fromHexString(changify(group.getColor(), getChanger(i))));
            Style ss = Style.style(TextColor.fromHexString(changify(group.getColor(), getChanger(i)))).font(Key.key("uniform"));
            first = false;
            builder.append(Component.text(i.formattedValue(i.sample()), s)).append(Component.text(i.formattedSuffix(i.sample()), ss));
        }

        return builder.build();
    }

    private Component writeHeader()
    {
        var builder = Component.text();
        boolean first = true;
        for(MonitorGroup i : configuration.getGroups())
        {
            if(!first)
            {
                builder.append(Component.space());
            }

            first = false;
            Sampler head = i.getHeadSampler();
            Double value = samplers.get(head);
            value = value == null ? 0 : value;

            String color = changify(i.getColor(), getChanger(head));

            Style s = focus == i ? Style.style(TextColor.fromHexString(color), TextDecoration.UNDERLINED)
                : Style.style(TextColor.fromHexString(color));
            Style ss = focus == i ? Style.style(TextColor.fromHexString(color), TextDecoration.UNDERLINED).font(Key.key("uniform"))
                : Style.style(TextColor.fromHexString(color)).font(Key.key("uniform"));

            builder.append(head.format(Component.text(head.formattedValue(value), s), Component.text(head.formattedSuffix(value), ss)));
        }

        return builder.build();
    }

    @Override
    public void flush() {
        clearVisibility();

        MonitorGroup f = focus;
        focus = getPlayer().isSneaking() ? configuration.getGroups().get(getPlayer().getScrollPosition(configuration.getGroups().size())) : null;

        if(focus != f) {
            if(focus != null) {
                focusUpAnimation = 5;
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

        React.adventure.player(getPlayer().getPlayer()).sendTitlePart(TitlePart.TIMES, new Title.Times() {
            @Override
            public @NotNull Duration fadeIn() {
                return Duration.ZERO;
            }

            @Override
            public @NotNull Duration stay() {
                return Duration.ofMillis(((int) ((getInterval() / 50) + 3)) * 50L);
            }

            @Override
            public @NotNull Duration fadeOut() {
                return Duration.ofMillis(20 * 50);
            }
        });

        if(focus != null) {
            if(focusUpAnimation >= -10) {
                if(focusUpAnimation > 0)
                {
                    React.adventure.player(getPlayer().getPlayer()).sendTitlePart(TitlePart.TITLE, writeHeaderTitle(focus));
                }
                else {
                    React.adventure.player(getPlayer().getPlayer()).sendTitlePart(TitlePart.TITLE, Component.space());
                }

                focusUpAnimation--;
            }

            React.adventure.player(getPlayer().getPlayer()).sendTitlePart(TitlePart.SUBTITLE, writeSubSamplers(focus));
        }

        else if(focusDownAnimation) {
            getPlayer().getPlayer().sendTitle(" ", "  ", 0, (int) ((getInterval() / 50) + 1), 17);
        }

        React.adventure.player(getPlayer().getPlayer()).sendActionBar(writeHeader());
    }
}
