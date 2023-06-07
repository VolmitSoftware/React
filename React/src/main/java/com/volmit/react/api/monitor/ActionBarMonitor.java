package com.volmit.react.api.monitor;

import com.volmit.react.React;
import com.volmit.react.api.monitor.configuration.MonitorConfiguration;
import com.volmit.react.api.monitor.configuration.MonitorGroup;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.model.ReactPlayer;
import com.volmit.react.util.math.M;
import com.volmit.react.util.scheduling.J;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class ActionBarMonitor extends PlayerMonitor {
    private int viewportIndex;
    private MonitorConfiguration configuration;
    private MonitorGroup focus;
    private Map<MonitorGroup, Integer> viewportIndexes;
    private Map<Sampler, Integer> maxLengths;
    private Map<Sampler, Double> lastValue;
    private Map<Sampler, Double> trends;
    private Map<Sampler, Long> lastTimes;
    private int focusUpAnimation = 0;
    private boolean focusDownAnimation = false;
    private boolean tickUp = false;
    private boolean locked = false;
    private int lockedPosition;
    private boolean running;

    public ActionBarMonitor(ReactPlayer player) {
        super("actionbar", player, 50);
        sleepDelay = 10;
        viewportIndexes = new HashMap<>();
        lastValue = new HashMap<>();
        maxLengths = new HashMap<>();
        lastTimes = new HashMap<>();
        trends = new HashMap<>();
        sleepingRate = 1000;
        viewportIndex = 0;
        lockedPosition = 0;
        configuration = player.getSettings().getMonitorConfiguration();
    }

    public boolean isAlwaysFlushing() {
        return true;
    }

    @Override
    public void stop() {
        running = false;
        try {
            J.ss(() -> getPlayer().getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(" ")), 3);
            J.ss(() -> React.audiences.player(getPlayer().getPlayer()).sendTitlePart(TitlePart.TITLE, Component.space()), 3);
            J.ss(() -> React.audiences.player(getPlayer().getPlayer()).sendTitlePart(TitlePart.SUBTITLE, Component.space()), 3);
        } catch (IllegalPluginAccessException e) {
            getPlayer().getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(" "));
            React.audiences.player(getPlayer().getPlayer()).sendTitlePart(TitlePart.TITLE, Component.space());
            React.audiences.player(getPlayer().getPlayer()).sendTitlePart(TitlePart.SUBTITLE, Component.space());
        }

        super.stop();
        unregister();
    }

    @Override
    public void start() {
        running = true;
        super.start();
    }

    private Component writeHeaderTitle(MonitorGroup group) {
        if (focusUpAnimation > 3) {
            return Component.text(group.getName()).style(Style.style(TextColor.fromHexString(group.getColor()), TextDecoration.BOLD));
        }

        if (focusUpAnimation > 2) {
            return Component.text(group.getName()).style(Style.style(TextColor.fromHexString(darker(group.getColor()))));
        }

        if (focusUpAnimation > 1) {
            return Component.text(group.getName()).style(Style.style(TextColor.fromHexString(darker(darker(group.getColor())))).font(Key.key("uniform")));
        }

        return Component.text(group.getName()).style(Style.style(TextColor.fromHexString(darker(darker(darker(group.getColor()))))).font(Key.key("uniform")));
    }

    private String changify(String color, double change) {
        Color c = new Color(Integer.parseInt(color.substring(1), 16));
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hsb[0] = (float) (M.lerp(hsb[0], hsb[0] + 0.05, change));
        hsb[1] = (float) (M.lerp(hsb[1] * 0.8, 1, change));
        hsb[2] = (float) (M.lerp(hsb[2] * 0.8, 1, change));
        return "#" + Integer.toString(Color.getHSBColor(hsb[0], hsb[1], hsb[2]).getRGB(), 16);
    }

    private String darker(String color) {
        return "#" + Integer.toString(new Color(Integer.parseInt(color.substring(1), 16)).darker().getRGB(), 16);
    }

    private Sampler getFocusedSampler() {
        return getFocusedSampler(focus);
    }

    private Sampler getFocusedSampler(MonitorGroup g) {
        return g.getHeadSampler();
    }

    private String colorActivity(String color, double activity) {
        Color c = new Color(Integer.parseInt(color.substring(1), 16));
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hsb[2] = (float) (hsb[2] * (0.8 + (activity * 0.2)));
        hsb[1] = (float) (hsb[1] * (0.7 + (activity * 0.3)));
        return "#" + Integer.toString(Color.getHSBColor(hsb[0], hsb[1], hsb[2]).getRGB(), 16);
    }

    private String colorTrend(String color, double direction) {
        Color c = new Color(Integer.parseInt(color.substring(1), 16));
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        float red = 0;
        float blue = 0.5f;
        float h = hsb[0];

        if (direction > 0) {
            h = (float) M.lerp(hsb[0], red, direction * 0.4);
        } else if (direction < 0) {
            direction = -direction;
            h = (float) M.lerp(hsb[0], blue, direction * 0.4);
        }

        return "#" + Integer.toString(Color.getHSBColor(h, hsb[1], hsb[2]).getRGB(), 16);
    }

    private String darkerColor(String color) {
        Color c = new Color(Integer.parseInt(color.substring(1), 16));
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        hsb[2] = (float) (hsb[2] * 0.8);
        hsb[1] = (float) (hsb[1] * 0.8);
        return "#" + Integer.toString(Color.getHSBColor(hsb[0], hsb[1], hsb[2]).getRGB(), 16);
    }

    private Double getSamplerValue(Sampler s) {
        Double value = samplers.get(s);

        if (value == null) {
            return value;
        }


        double trend = trends.getOrDefault(s, 0D);
        if (lastValue.get(s) == null || !value.equals(lastValue.get(s))) {
            if (lastValue.get(s) != null) {
                double before = lastValue.get(s);
                double after = value;
                double percentChange = (after - before) / before;
                trend = (percentChange + (trend * 10)) / 11D;
            } else {
                trend *= 0.99D;
            }

            lastValue.put(s, value);
            lastTimes.put(s, System.currentTimeMillis());

        } else {
            trend *= 0.99D;
        }

        trend = Double.isInfinite(trend) || Double.isNaN(trend) ? 0 : trend;
        trends.put(s, trend);


        return value;
    }

    private double getActivity(Sampler s, long span) {
        if (lastTimes.containsKey(s)) {
            long time = System.currentTimeMillis() - span;
            if (lastTimes.get(s) < time) {
                return 0;
            }

            return 1 - ((System.currentTimeMillis() - lastTimes.get(s)) / (double) span);
        }

        return 0;
    }

    private Component writeSubSamplers() {
        if (!viewportIndexes.containsKey(focus)) {
            viewportIndexes.put(focus, 0);
        }

        int viewportLimit = Math.min(5, focus.getSamplers().size());
        int index = focus.getSamplers().indexOf(getFocusedSampler().getId());
        int viewportIndex = viewportIndexes.get(focus);

        while (index + 1 > viewportIndex + viewportLimit) {
            viewportIndex++;
        }

        while (index < viewportIndex) {
            viewportIndex--;
        }

        viewportIndexes.put(focus, viewportIndex);

        var builder = Component.text();
        boolean first = true;
        for (int m = 0; m < viewportLimit; m++) {
            try {
                Sampler i = React.sampler(focus.getSamplers()
                        .get((viewportIndexes.get(focus) + m) % focus.getSamplers().size()));
                setVisible(i, true);
                if (!first) {
                    builder.append(Component.space());
                }

                first = false;
                Double value = getSamplerValue(i);
                value = value == null ? 0 : value;

                String color = colorTrend(colorActivity(focus.getColor(), getActivity(i, 5000)), trends.getOrDefault(i, 0D));
                String colorD = darkerColor(color);

                Style s = (locked && getPlayer().isMonitorSneaking() && getFocusedSampler() == i) ? Style.style(TextColor.fromHexString(color), TextDecoration.UNDERLINED)
                        : Style.style(TextColor.fromHexString(color));
                Style ss = (locked && getPlayer().isMonitorSneaking() && getFocusedSampler() == i) ? Style.style(TextColor.fromHexString(color), TextDecoration.UNDERLINED).font(Key.key("uniform"))
                        : Style.style(TextColor.fromHexString(colorD)).font(Key.key("uniform"));

                int l = i.format(value).length();
                synchronized (maxLengths) {
                    maxLengths.compute(i, (k, v) -> Math.max(v == null ? 0 : v, l));
                }
                builder.append(i.format(Component.text(i.formattedValue(value), s), Component.text(i.formattedSuffix(value), ss)));

                if (l < maxLengths.get(i)) {
                    builder.append(Component.text(" ".repeat(maxLengths.get(i) - l)));
                }
            } catch (Throwable e) {
                e.printStackTrace();
                viewportIndexes.put(focus, 0);
            }
        }

        return builder.build();
    }

    private Component writeHeader() {
        int viewportLimit = Math.min(5, configuration.getGroups().size());
        if (focus != null) {
            int index = configuration.getGroups().indexOf(focus);

            while (index + 1 > viewportIndex + viewportLimit) {
                viewportIndex++;
            }

            while (index < viewportIndex) {
                viewportIndex--;
            }
        }

        var builder = Component.text();
        boolean first = true;
        for (int m = 0; m < viewportLimit; m++) {
            MonitorGroup i = configuration.getGroups().get((viewportIndex + m) % configuration.getGroups().size());
            setVisible(getFocusedSampler(i), true);

            if (!first) {
                builder.append(Component.space());
            }

            first = false;
            Sampler head = getFocusedSampler(i);
            Double value = getSamplerValue(head);
            value = value == null ? 0 : value;

            String color = colorTrend(colorActivity(i.getColor(), getActivity(head, 5000)), trends.getOrDefault(head, 0D));
            String colorD = darkerColor(color);

            Style s = focus == i ? Style.style(TextColor.fromHexString(color), TextDecoration.UNDERLINED)
                    : Style.style(TextColor.fromHexString(color));
            Style ss = focus == i ? Style.style(TextColor.fromHexString(color), TextDecoration.UNDERLINED).font(Key.key("uniform"))
                    : Style.style(TextColor.fromHexString(colorD)).font(Key.key("uniform"));
            int l = head.format(value).length();
            synchronized (maxLengths) {
                maxLengths.compute(head, (k, v) -> Math.max(v == null ? 0 : v, l));
            }
            builder.append(head.format(Component.text(head.formattedValue(value), s), Component.text(head.formattedSuffix(value), ss)));

            if (l < maxLengths.get(head)) {
                builder.append(Component.text(" ".repeat(maxLengths.get(head) - l)));
            }
        }

        return builder.build();
    }

    MonitorGroup getFocusedHeaderGroup() {
        return locked ? configuration.getGroups().get(lockedPosition) : getPlayer().isMonitorSneaking() ? configuration.getGroups().get(getPlayer().getScrollPosition(configuration.getGroups().size())) : null;
    }

    Sampler getNextFocusedSampler() {
        return React.sampler(focus.getSamplers().get(getPlayer().getScrollPosition(focus.getSamplers().size())));
    }

    @Override
    public void flush() {
        if (!running) {
            return;
        }
        clearVisibility();
        MonitorGroup f = focus;

        if (locked != getPlayer().isLocked()) {
            locked = getPlayer().isLocked();
            lockedPosition = getPlayer().getScrollPosition(configuration.getGroups().size());
        }

        focus = getFocusedHeaderGroup();

        if (focus != f) {
            if (focus != null) {
                focusUpAnimation = 5;
            } else {
                focusDownAnimation = true;
            }
        }

        if (focus != null) {
            if (locked && getPlayer().isMonitorSneaking()) {
                focus.setHeadSampler(getNextFocusedSampler().getId());
            }

            React.audiences.player(getPlayer().getPlayer()).sendTitlePart(TitlePart.TIMES, new Title.Times() {
                @Override
                public @NotNull Duration fadeIn() {
                    return Duration.ZERO;
                }

                @Override
                public @NotNull Duration stay() {
                    return Duration.ofMillis(((int) ((getTinterval() / 50) + 3)) * 50L);
                }

                @Override
                public @NotNull Duration fadeOut() {
                    return Duration.ofMillis(20 * 50);
                }
            });

            if (focusUpAnimation >= -10) {
                if (focusUpAnimation > 0) {
                    React.audiences.player(getPlayer().getPlayer()).sendTitlePart(TitlePart.TITLE, writeHeaderTitle(focus));
                } else {
                    React.audiences.player(getPlayer().getPlayer()).sendTitlePart(TitlePart.TITLE, Component.space());
                }

                focusUpAnimation--;
            }

            React.audiences.player(getPlayer().getPlayer()).sendTitlePart(TitlePart.SUBTITLE, writeSubSamplers());
        } else if (focusDownAnimation) {
            getPlayer().getPlayer().sendTitle(" ", "  ", 0, (int) ((getTinterval() / 50) + 1), 17);
        }

        React.audiences.player(getPlayer().getPlayer()).sendActionBar(writeHeader());
    }
}
