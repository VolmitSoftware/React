package com.volmit.react.api.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Sound;

import java.awt.*;

public class RConst {
    public static final Color COLOR_ERROR = new Color(255, 0, 0);
    public static final Color COLOR_SUCCESS = new Color(0, 255, 0);
    public static final Color COLOR_WARNING = new Color(255, 255, 0);
    public static final Color COLOR_INFO = new Color(255, 255, 255);
    public static final Feedback TELEPORT = Feedback.builder()
            .sound(SoundFeedback.builder()
                    .sound(Sound.ENTITY_ENDER_EYE_LAUNCH)
                    .volume(0.7f)
                    .pitch(1.25f)
                    .build())
            .build();

    public static Feedback error(String message, Object... args) {
        return Feedback.builder()
                .message(errorText(message, args))
                .sound(SoundFeedback.builder().sound(Sound.BLOCK_DEEPSLATE_BREAK).pitch(0.5f).volume(1f).build())
                .build();
    }

    public static Feedback success(String message, Object... args) {
        return Feedback.builder()
                .message(successText(message, args))
                .sound(SoundFeedback.builder().sound(Sound.BLOCK_AMETHYST_BLOCK_PLACE).pitch(1.5f).volume(1f).build())
                .sound(SoundFeedback.builder().sound(Sound.ITEM_ARMOR_EQUIP_ELYTRA).pitch(1.1f).volume(1f).build())
                .build();
    }

    public static Feedback warning(String message, Object... args) {
        return Feedback.builder()
                .message(warningText(message, args))
                .sound(SoundFeedback.builder().sound(Sound.ITEM_ARMOR_EQUIP_CHAIN).pitch(0.6f).volume(1f).build())
                .build();
    }

    public static Feedback info(String message, Object... args) {
        return Feedback.builder()
                .message(infoText(message, args))
                .sound(SoundFeedback.builder().sound(Sound.ITEM_ARMOR_EQUIP_LEATHER).pitch(1.1f).volume(1f).build())
                .build();
    }

    public static net.kyori.adventure.text.TextComponent errorText(String message, Object... args) {
        return net.kyori.adventure.text.Component.text(message.formatted(args)).color(TextColor.color(RConst.COLOR_ERROR.getRGB()));
    }

    public static net.kyori.adventure.text.TextComponent successText(String message, Object... args) {
        return net.kyori.adventure.text.Component.text(message.formatted(args)).color(TextColor.color(RConst.COLOR_SUCCESS.getRGB()));
    }

    public static net.kyori.adventure.text.TextComponent warningText(String message, Object... args) {
        return net.kyori.adventure.text.Component.text(message.formatted(args)).color(TextColor.color(RConst.COLOR_WARNING.getRGB()));
    }

    public static net.kyori.adventure.text.TextComponent infoText(String message, Object... args) {
        return Component.text(message.formatted(args)).color(TextColor.color(RConst.COLOR_INFO.getRGB()));
    }

}
