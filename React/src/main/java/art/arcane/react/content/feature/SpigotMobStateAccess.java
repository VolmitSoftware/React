package art.arcane.react.content.feature;

import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Tameable;

import java.util.UUID;

final class SpigotMobStateAccess implements MobStateAccess {
  @Override
  public boolean isIgnited(Creeper creeper) {
    return false;
  }

  @Override
  public void clearIgnition(Creeper creeper) {
  }

  @Override
  public boolean isProvoked(Enderman enderman) {
    return false;
  }

  @Override
  public void copyProvocation(Enderman source, Enderman target) {
  }

  @Override
  public boolean sameSoundVariant(Cow left, Cow right) {
    return true;
  }

  @Override
  public boolean sameSoundVariant(Chicken left, Chicken right) {
    return true;
  }

  @Override
  public boolean sameSoundVariant(Pig left, Pig right) {
    return true;
  }

  @Override
  public void copySoundVariant(Cow source, Cow target) {
  }

  @Override
  public void copySoundVariant(Chicken source, Chicken target) {
  }

  @Override
  public void copySoundVariant(Pig source, Pig target) {
  }

  @Override
  public boolean isChickenJockey(Chicken chicken) {
    return false;
  }

  @Override
  public void copyChickenState(Chicken source, Chicken target) {
  }

  @Override
  public UUID ownerId(Tameable tameable) {
    AnimalTamer owner = tameable.getOwner();
    return owner == null ? null : owner.getUniqueId();
  }
}
