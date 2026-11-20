package art.arcane.react.platform.bukkit.command;

import art.arcane.react.React;
import art.arcane.react.content.directorcommand.EntityDataCommands;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.platform.bukkit.BukkitGameEntity;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.platform.bukkit.BukkitCommandSender;
import art.arcane.react.util.plugin.VolmitSender;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class BukkitCommandDebug {
  public void dump(VolmitSender sender, boolean upload) {
    React.instance.debugDump().request(BukkitCommandSender.bukkit(sender), upload);
  }

  public void entityData(VolmitSender sender) {
    Vector look = BukkitCommandSender.player(sender).getLocation().getDirection().multiply(1);
    Location buf = BukkitCommandSender.player(sender).getLocation().clone().add(look);
    ray:
    for (int i = 0; i < 16; i++) {
      buf.add(look);
      for (Entity j : buf.getWorld().getNearbyEntities(buf, 2, 2, 2)) {
        if (j.equals(BukkitCommandSender.player(sender))) {
          continue;
        }
        if (j instanceof LivingEntity living) {
          J.runEntity(living, () -> living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20, 0, false, false)));
        }
        EntityDataCommands.send(BukkitCommandSender.player(sender), ReactEntity.getPriority(new BukkitGameEntity(j)), ReactEntity.getCrowding(new BukkitGameEntity(j)),
            ReactEntity.getNearestPlayer(new BukkitGameEntity(j)), ReactEntity.getStaleness(new BukkitGameEntity(j)));
        break ray;
      }
    }
  }
}
