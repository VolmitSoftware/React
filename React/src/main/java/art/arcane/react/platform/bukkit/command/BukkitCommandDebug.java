package art.arcane.react.platform.bukkit.command;

import art.arcane.react.React;
import art.arcane.react.model.ReactEntity;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.platform.bukkit.BukkitCommandSender;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
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
        ReactLanguage.send(BukkitCommandSender.player(sender), CommandMessages.DEBUG_PRIORITY, MessageArgument.untrusted("value", Form.f((int) ReactEntity.getPriority(j))));
        ReactLanguage.send(BukkitCommandSender.player(sender), CommandMessages.DEBUG_CROWDING, MessageArgument.untrusted("value", Form.f((int) ReactEntity.getCrowding(j))));
        ReactLanguage.send(BukkitCommandSender.player(sender), CommandMessages.DEBUG_NEAREST_PLAYER, MessageArgument.untrusted("value", Form.f(ReactEntity.getNearestPlayer(j), 1)));
        ReactLanguage.send(BukkitCommandSender.player(sender), CommandMessages.DEBUG_UPDATED, MessageArgument.untrusted("duration", Form.duration(ReactEntity.getStaleness(j), 0)));
        break ray;
      }
    }
  }
}
