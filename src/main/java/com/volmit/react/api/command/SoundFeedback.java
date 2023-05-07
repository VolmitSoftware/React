package com.volmit.react.api.command;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

@Builder
@Data
@Accessors(chain = true, fluent = true)
public class SoundFeedback {
    private Sound sound;
    @Builder.Default
    private float volume = 1f;
    @Builder.Default
    private float pitch = 1f;

    public void play(Player p) {
        p.playSound(p.getLocation(), sound, volume, pitch);
    }
}
