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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ActionBarMonitor extends PlayerMonitor {
    private int viewportIndex;
    private MonitorConfiguration configuration;
    private MonitorGroup focus;
    private Map<MonitorGroup, Integer> viewportIndexes;
    private Map<MonitorGroup, Sampler> focusedSamplers;
    private Map<Sampler, Integer> maxLengths;
    private int focusUpAnimation = 0;
    private boolean focusDownAnimation = false;
    private boolean tickUp = false;
    private boolean locked = false;
    private int lockedPosition;

    public ActionBarMonitor(ReactPlayer player) {
        super("actionbar", player, 50);
        sleepDelay = 10;
        viewportIndexes = new HashMap<>();
        focusedSamplers = new HashMap<>();
        maxLengths = new HashMap<>();
        sleepingRate = 1000;
        viewportIndex = 0;
        lockedPosition = 0;
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
        unregister();
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

    private Sampler getFocusedSampler()
    {
        return getFocusedSampler(focus);
    }

    private Sampler getFocusedSampler(MonitorGroup g)
    {
        Sampler s = focusedSamplers.get(g);

        if(s== null)
        {
            s = g.getHeadSampler();
            focusedSamplers.put(g, s);
        }

        return s;
    }

    private Component writeSubSamplers() {
        if(!viewportIndexes.containsKey(focus)) {
            viewportIndexes.put(focus, 0);
        }

        int viewportLimit = Math.min(5, focus.getSamplers().size());
        int index = focus.getSamplers().indexOf(getFocusedSampler().getId());
        int viewportIndex = viewportIndexes.get(focus);

        while(index+1 > viewportIndex + viewportLimit) {
            viewportIndex++;
        }

        while(index < viewportIndex) {
            viewportIndex--;
        }

        viewportIndexes.put(focus, viewportIndex);

        var builder = Component.text();
        boolean first = true;
        for(int m = 0; m < viewportLimit; m++) {
            Sampler i = React.instance.getSampleController().getSampler(focus.getSamplers().get((viewportIndexes.get(focus) + m) % focus.getSamplers().size()));

            setVisible(i, true);
            if(!first) {
                builder.append(Component.space());
            }

            first = false;
            Double value = samplers.get(i);
            value = value == null ? 0 : value;

            String color = focus.getColor();

            Style s = (locked && getPlayer().isSneaking() && getFocusedSampler() == i) ? Style.style(TextColor.fromHexString(color), TextDecoration.UNDERLINED)
                : Style.style(TextColor.fromHexString(color));
            Style ss = (locked && getPlayer().isSneaking() && getFocusedSampler() == i) ? Style.style(TextColor.fromHexString(color), TextDecoration.UNDERLINED).font(Key.key("uniform"))
                : Style.style(TextColor.fromHexString(color)).font(Key.key("uniform"));

            int l = i.format(value).length();
            synchronized(maxLengths) {
                maxLengths.compute(i, (k,v) -> Math.max(v == null ? 0 : v, l));
            }
            builder.append(i.format(Component.text(i.formattedValue(value), s), Component.text(i.formattedSuffix(value), ss)));

            if(l < maxLengths.get(i)) {
                builder.append(Component.text(" ".repeat(maxLengths.get(i) - l)));
            }
        }

        return builder.build();
    }

    private Component writeHeader() {
        int viewportLimit = Math.min(5, configuration.getGroups().size());
        if(focus != null) {
            int index = configuration.getGroups().indexOf(focus);

            while(index+1 > viewportIndex + viewportLimit) {
                viewportIndex++;
            }

            while(index < viewportIndex) {
                viewportIndex--;
            }
        }

        var builder = Component.text();
        boolean first = true;
        for(int m = 0; m < viewportLimit; m++) {
            MonitorGroup i = configuration.getGroups().get((viewportIndex + m) % configuration.getGroups().size());
            setVisible(getFocusedSampler(i), true);

            if(!first) {
                builder.append(Component.space());
            }

            first = false;
            Sampler head = getFocusedSampler(i);
            Double value = samplers.get(head);
            value = value == null ? 0 : value;

            String color = i.getColor();

            Style s = focus == i ? Style.style(TextColor.fromHexString(color), TextDecoration.UNDERLINED)
                : Style.style(TextColor.fromHexString(color));
            Style ss = focus == i ? Style.style(TextColor.fromHexString(color), TextDecoration.UNDERLINED).font(Key.key("uniform"))
                : Style.style(TextColor.fromHexString(color)).font(Key.key("uniform"));
            int l = head.format(value).length();
            synchronized(maxLengths) {
                maxLengths.compute(head, (k,v) -> Math.max(v == null ? 0 : v, l));
            }
            builder.append(head.format(Component.text(head.formattedValue(value), s), Component.text(head.formattedSuffix(value), ss)));

            if(l < maxLengths.get(head)) {
                builder.append(Component.text(" ".repeat(maxLengths.get(head) - l)));
            }
        }

        return builder.build();
    }

    MonitorGroup getFocusedHeaderGroup(){
        return locked ? configuration.getGroups().get(lockedPosition) : getPlayer().isSneaking() ? configuration.getGroups().get(getPlayer().getScrollPosition(configuration.getGroups().size())) : null;
    }

    Sampler getNextFocusedSampler() {
        return React.instance.getSampleController().getSampler(focus.getSamplers().get(getPlayer().getScrollPosition(focus.getSamplers().size())));
    }

    @Override
    public void flush() {
        clearVisibility();
        MonitorGroup f = focus;

        if(locked != getPlayer().isLocked())
        {
            locked = getPlayer().isLocked();
            lockedPosition = getPlayer().getScrollPosition(configuration.getGroups().size());
        }

        focus = getFocusedHeaderGroup();

        if(focus != f) {
            if(focus != null) {
                focusUpAnimation = 5;
            }

            else {
                focusDownAnimation = true;
            }
        }

        if(focus != null) {
            if(locked && getPlayer().isSneaking())
            {
                focusedSamplers.put(focus, getNextFocusedSampler());
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

            React.adventure.player(getPlayer().getPlayer()).sendTitlePart(TitlePart.SUBTITLE, writeSubSamplers());
        }

        else if(focusDownAnimation) {
            getPlayer().getPlayer().sendTitle(" ", "  ", 0, (int) ((getInterval() / 50) + 1), 17);
        }

        React.adventure.player(getPlayer().getPlayer()).sendActionBar(writeHeader());
    }
}
