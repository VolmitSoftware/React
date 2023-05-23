package com.volmit.react.content.decreecommand;

import com.volmit.react.React;
import com.volmit.react.core.controller.PlayerController;
import com.volmit.react.core.gui.MonitorConfigGUI;
import com.volmit.react.model.ReactEntity;
import com.volmit.react.util.decree.DecreeExecutor;
import com.volmit.react.util.decree.DecreeOrigin;
import com.volmit.react.util.decree.annotations.Decree;
import com.volmit.react.util.format.Form;
import com.volmit.react.util.scheduling.J;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

@Decree(
        name = "config",
        aliases = {"conf", "cfg", "c"},
        origin = DecreeOrigin.BOTH,
        description = "The configuration command"
)
public class CommandConfig implements DecreeExecutor {
    @Decree(
            name = "monitor",
            aliases = {"m", "mon"},
            description = "Configure the monitor",
            origin = DecreeOrigin.PLAYER
    )
    public void monitor() {
        Player player = player();
        MonitorConfigGUI.editMonitorConfiguration(player, React.controller(PlayerController.class).getPlayer(player).getSettings().getMonitorConfiguration(),
                (c) -> React.controller(PlayerController.class).getPlayer(player).saveSettings());
    }

    @Decree(
        name = "entity-data",
        aliases = {"edata"},
        description = "Show Entity Data for the entity looked at",
        origin = DecreeOrigin.PLAYER,
        sync = true
    )
    public void entityData() {
        Vector look = player().getLocation().getDirection().multiply(1);
        Location buf = player().getLocation().clone().add(look);

        ray: for(int i = 0; i < 16; i++) {
            buf.add(look);

            for(Entity j : buf.getWorld().getNearbyEntities(buf, 2, 2, 2)) {
                if(j.equals(player())) {
                    continue;
                }

                j.setGlowing(true);

                J.s(() -> j.setGlowing(false), 1);

                player().sendMessage("Priority: " + Form.f((int)ReactEntity.getPriority(j)));
                player().sendMessage("Crowding: " + Form.f((int)ReactEntity.getCrowding(j)));
                player().sendMessage("Player N: " + Form.f(ReactEntity.getNearestPlayer(j), 1));
                player().sendMessage("Updated : " + Form.duration(ReactEntity.getStaleness(j), 0) + " ago");
                break ray;
            }
        }
    }
}
