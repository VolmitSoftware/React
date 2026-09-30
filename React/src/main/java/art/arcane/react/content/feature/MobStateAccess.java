package art.arcane.react.content.feature;

import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Tameable;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

interface MobStateAccess {
  static MobStateAccess detect() {
    return supportsPaperState() ? new PaperMobStateAccess() : new SpigotMobStateAccess();
  }

  static boolean supportsPaperState() {
    return hasMethods(Creeper.class, List.of("isIgnited", "setIgnited"))
        && hasMethods(Enderman.class, List.of("isScreaming", "setScreaming", "hasBeenStaredAt", "setHasBeenStaredAt"))
        && hasMethods(Chicken.class, List.of("getSoundVariant", "setSoundVariant", "isChickenJockey",
            "setIsChickenJockey", "getEggLayTime", "setEggLayTime"))
        && hasMethods(Cow.class, List.of("getSoundVariant", "setSoundVariant"))
        && hasMethods(Pig.class, List.of("getSoundVariant", "setSoundVariant"))
        && hasMethods(Tameable.class, List.of("getOwnerUniqueId"));
  }

  private static boolean hasMethods(Class<?> type, List<String> names) {
    try {
      Method[] methods = type.getMethods();
      Set<String> available = new HashSet<>(methods.length * 2);
      for (Method method : methods) {
        available.add(method.getName());
      }
      return available.containsAll(names);
    } catch (LinkageError | SecurityException probeFailure) {
      return false;
    }
  }

  boolean isIgnited(Creeper creeper);

  void clearIgnition(Creeper creeper);

  boolean isProvoked(Enderman enderman);

  void copyProvocation(Enderman source, Enderman target);

  boolean sameSoundVariant(Cow left, Cow right);

  boolean sameSoundVariant(Chicken left, Chicken right);

  boolean sameSoundVariant(Pig left, Pig right);

  void copySoundVariant(Cow source, Cow target);

  void copySoundVariant(Chicken source, Chicken target);

  void copySoundVariant(Pig source, Pig target);

  boolean isChickenJockey(Chicken chicken);

  void copyChickenState(Chicken source, Chicken target);

  UUID ownerId(Tameable tameable);
}
