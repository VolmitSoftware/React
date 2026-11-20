package art.arcane.react.content.feature;

import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ItemHost;
import art.arcane.react.platform.Subscription;
import art.arcane.react.platform.TestGameEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.UUID;
import java.util.function.Consumer;

class FeatureItemSuperStackerCoreTest {
  private static final GameLocation SPOT = new GameLocation(UUID.randomUUID(), 8.5D, 64D, 8.5D);

  @Test
  void matchingStacksMergeUpToTheVanillaMaximum() {
    ItemHost host = host();
    TestGameEntity source = new TestGameEntity("ITEM", SPOT);
    TestGameEntity target = new TestGameEntity("ITEM", SPOT);
    Mockito.when(host.amount(source)).thenReturn(4);
    Mockito.when(host.amount(target)).thenReturn(60);
    Mockito.when(host.maxStackSize(target)).thenReturn(64);
    Mockito.when(host.isSimilar(source, target)).thenReturn(true);
    FeatureItemSuperStacker feature = new FeatureItemSuperStacker(host);

    feature.onActivate();
    spawn(host, target);
    feature.mergeWithNearbyItems(source);
    feature.onDeactivate();

    Mockito.verify(host).setAmount(target, 64);
    Mockito.verify(host).discardMerged(source);
    Mockito.verify(host, Mockito.never()).setAmount(Mockito.eq(source), Mockito.anyInt());
  }

  @Test
  void mixedStacksPackIntoOneSuperStackAboveTheVanillaMaximum() {
    ItemHost host = host();
    TestGameEntity source = new TestGameEntity("ITEM", SPOT);
    TestGameEntity target = new TestGameEntity("ITEM", SPOT);
    Mockito.when(host.amount(source)).thenReturn(40);
    Mockito.when(host.amount(target)).thenReturn(20);
    Mockito.when(host.bundle(source, target, 64)).thenReturn(true);
    FeatureItemSuperStacker feature = new FeatureItemSuperStacker(host);

    feature.onActivate();
    spawn(host, target);
    feature.mergeWithNearbyItems(source);

    Mockito.verify(host).discardMerged(source);
    Mockito.verify(host).refreshLabel(Mockito.eq(target), Mockito.anyString(), Mockito.anyString(),
        Mockito.anyString(), Mockito.eq(3));

    TestGameEntity third = new TestGameEntity("ITEM", SPOT);
    Mockito.when(host.amount(third)).thenReturn(10);
    feature.mergeWithNearbyItems(third);
    feature.onDeactivate();

    Mockito.verify(host, Mockito.never()).bundle(Mockito.eq(third), Mockito.any(GameEntity.class), Mockito.anyInt());
  }

  @Test
  void containerPickupKeepsTheResidualSuperStack() {
    ItemHost host = host();
    TestGameEntity item = new TestGameEntity("ITEM", SPOT);
    Mockito.when(host.isSuperStack(item)).thenReturn(true);
    ItemHost.ContainerPickup pickup = Mockito.mock(ItemHost.ContainerPickup.class);
    Mockito.when(pickup.item()).thenReturn(item);
    Mockito.when(pickup.insertContents()).thenReturn(ItemHost.Insertion.KEPT);
    Mockito.when(pickup.keptCount()).thenReturn(12);
    FeatureItemSuperStacker feature = new FeatureItemSuperStacker(host);

    feature.onContainerPickup(pickup);

    Mockito.verify(pickup).cancel();
    Mockito.verify(host, Mockito.never()).discardMerged(item);

    Mockito.when(pickup.insertContents()).thenReturn(ItemHost.Insertion.INSERTED);
    feature.onContainerPickup(pickup);
    Mockito.verify(host).discardMerged(item);
  }

  @Test
  void playerPickupUnpacksOnlySuperStacks() {
    ItemHost host = host();
    TestGameEntity plain = new TestGameEntity("ITEM", SPOT);
    TestGameEntity stacked = new TestGameEntity("ITEM", SPOT);
    Mockito.when(host.isSuperStack(stacked)).thenReturn(true);
    ItemHost.PlayerPickup plainPickup = Mockito.mock(ItemHost.PlayerPickup.class);
    ItemHost.PlayerPickup stackedPickup = Mockito.mock(ItemHost.PlayerPickup.class);
    Mockito.when(plainPickup.item()).thenReturn(plain);
    Mockito.when(stackedPickup.item()).thenReturn(stacked);
    FeatureItemSuperStacker feature = new FeatureItemSuperStacker(host);

    feature.onPlayerPickup(plainPickup);
    feature.onPlayerPickup(stackedPickup);

    Mockito.verify(plainPickup, Mockito.never()).cancel();
    Mockito.verify(stackedPickup).cancel();
    Mockito.verify(stackedPickup).giveContents();
    Mockito.verify(host).discardMerged(stacked);
  }

  @Test
  void superStacksResistEverythingButTheVoid() {
    ItemHost host = host();
    TestGameEntity item = new TestGameEntity("ITEM", SPOT);
    Mockito.when(host.isSuperStack(item)).thenReturn(true);
    ItemHost.Damage fire = Mockito.mock(ItemHost.Damage.class);
    ItemHost.Damage fall = Mockito.mock(ItemHost.Damage.class);
    Mockito.when(fire.item()).thenReturn(item);
    Mockito.when(fall.item()).thenReturn(item);
    Mockito.when(fall.isVoid()).thenReturn(true);
    FeatureItemSuperStacker feature = new FeatureItemSuperStacker(host);

    feature.onDamage(fire);
    feature.onDamage(fall);

    Mockito.verify(fire).cancel();
    Mockito.verify(fall, Mockito.never()).cancel();
  }

  @Test
  void integrityHooksOpenOnceAndCloseOnDetach() {
    ItemHost host = host();
    Subscription pickup = Mockito.mock(Subscription.class);
    Mockito.when(host.onPlayerPickup(Mockito.any())).thenReturn(pickup);
    FeatureItemSuperStacker feature = new FeatureItemSuperStacker(host);

    feature.onAttach();
    feature.onAttach();
    feature.onDetach();

    Mockito.verify(host, Mockito.times(1)).onPlayerPickup(Mockito.any());
    Mockito.verify(pickup).cancel();
  }

  @SuppressWarnings("unchecked")
  private static void spawn(ItemHost host, GameEntity item) {
    ArgumentCaptor<Consumer<GameEntity>> listener = ArgumentCaptor.forClass(Consumer.class);
    Mockito.verify(host, Mockito.atLeastOnce()).onSpawn(listener.capture());
    listener.getValue().accept(item);
  }

  private static ItemHost host() {
    ItemHost host = Mockito.mock(ItemHost.class);
    Mockito.when(host.isLive(Mockito.any())).thenReturn(true);
    Mockito.when(host.superStackCount(Mockito.any())).thenReturn(-1);
    Mockito.when(host.entity(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));
    Mockito.when(host.onSpawn(Mockito.any())).thenReturn(Subscription.NONE);
    Mockito.when(host.onLoad(Mockito.any())).thenReturn(Subscription.NONE);
    Mockito.when(host.onPlayerPickup(Mockito.any())).thenReturn(Subscription.NONE);
    Mockito.when(host.onContainerPickup(Mockito.any())).thenReturn(Subscription.NONE);
    Mockito.when(host.onSlotClick(Mockito.any())).thenReturn(Subscription.NONE);
    Mockito.when(host.onDamage(Mockito.any())).thenReturn(Subscription.NONE);
    Mockito.when(host.onRemove(Mockito.any())).thenReturn(Subscription.NONE);
    return host;
  }
}
