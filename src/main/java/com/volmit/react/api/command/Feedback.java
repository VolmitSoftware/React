package com.volmit.react.api.command;

import com.volmit.react.React;
import com.volmit.react.util.C;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@Builder
@Data
@Accessors(chain = true, fluent = true)
public class Feedback {
    @Singular
    private List<SoundFeedback> sounds;
    @Singular
    private List<TextComponent> messages;

    public void send(CommandSender serverOrPlayer) {
        if (serverOrPlayer instanceof Player p) {
            for (SoundFeedback i : sounds) {
                i.play(p);
            }
        }

        for (TextComponent i : messages) {
            String prefix = C.GRAY + "[" + C.GOLD + "Foundation" + C.GRAY + "]:";
            React.audiences.sender(serverOrPlayer).sendMessage(i.content(prefix + " " + i.content()));
        }
    }
}
