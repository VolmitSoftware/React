package art.arcane.react.content.feature;

import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Tameable;

import java.util.Objects;
import java.util.UUID;

final class PaperMobStateAccess implements MobStateAccess {
  @Override
  public boolean isIgnited(Creeper creeper) {
    return creeper.isIgnited();
  }

  @Override
  public void clearIgnition(Creeper creeper) {
    creeper.setIgnited(false);
  }

  @Override
  public boolean isProvoked(Enderman enderman) {
    return enderman.isScreaming() || enderman.hasBeenStaredAt();
  }

  @Override
  public void copyProvocation(Enderman source, Enderman target) {
    target.setScreaming(source.isScreaming());
    target.setHasBeenStaredAt(source.hasBeenStaredAt());
  }

  @Override
  public boolean sameSoundVariant(Cow left, Cow right) {
    return Objects.equals(left.getSoundVariant(), right.getSoundVariant());
  }

  @Override
  public boolean sameSoundVariant(Chicken left, Chicken right) {
    return Objects.equals(left.getSoundVariant(), right.getSoundVariant());
  }

  @Override
  public boolean sameSoundVariant(Pig left, Pig right) {
    return Objects.equals(left.getSoundVariant(), right.getSoundVariant());
  }

  @Override
  public void copySoundVariant(Cow source, Cow target) {
    target.setSoundVariant(source.getSoundVariant());
  }

  @Override
  public void copySoundVariant(Chicken source, Chicken target) {
    target.setSoundVariant(source.getSoundVariant());
  }

  @Override
  public void copySoundVariant(Pig source, Pig target) {
    target.setSoundVariant(source.getSoundVariant());
  }

  @Override
  public boolean isChickenJockey(Chicken chicken) {
    return chicken.isChickenJockey();
  }

  @Override
  public void copyChickenState(Chicken source, Chicken target) {
    target.setIsChickenJockey(source.isChickenJockey());
    target.setEggLayTime(source.getEggLayTime());
  }

  @Override
  public UUID ownerId(Tameable tameable) {
    return tameable.getOwnerUniqueId();
  }
}
