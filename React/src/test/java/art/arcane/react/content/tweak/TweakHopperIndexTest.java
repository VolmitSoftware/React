package art.arcane.react.content.tweak;

import art.arcane.react.content.feature.FeatureHopperContainerThroughputMap;
import art.arcane.react.core.bridge.BridgeKind;
import art.arcane.react.core.bridge.NmsBridgeDescriptor;
import art.arcane.react.core.bridge.NmsBridgeHandle;
import art.arcane.react.core.bridge.NmsBridgeRegistry;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TweakHopperIndexTest {

    @Test
    public void bridgeIdConstantsExistAndNonEmpty() {
        Assert.assertNotNull(TweakHopperIndex.BRIDGE_ADD_ITEM);
        Assert.assertNotNull(TweakHopperIndex.BRIDGE_COOLDOWN_TIME);
        Assert.assertNotNull(TweakHopperIndex.BRIDGE_GET_BLOCK_ENTITY);
        Assert.assertNotNull(TweakHopperIndex.BRIDGE_BLOCK_POS_CTOR);
        Assert.assertFalse(TweakHopperIndex.BRIDGE_ADD_ITEM.isEmpty());
        Assert.assertFalse(TweakHopperIndex.BRIDGE_COOLDOWN_TIME.isEmpty());
        Assert.assertFalse(TweakHopperIndex.BRIDGE_GET_BLOCK_ENTITY.isEmpty());
        Assert.assertFalse(TweakHopperIndex.BRIDGE_BLOCK_POS_CTOR.isEmpty());
    }

    @Test
    public void hopperBridgeDescriptorsReturnsFourEntries() {
        List<NmsBridgeDescriptor> descriptors = TweakHopperIndex.hopperBridgeDescriptors();
        Assert.assertEquals(4, descriptors.size());
    }

    @Test
    public void addItemDescriptorIsStaticMethodKind() {
        NmsBridgeDescriptor d = descriptorById(TweakHopperIndex.BRIDGE_ADD_ITEM);
        Assert.assertNotNull("addItem descriptor must be present", d);
        Assert.assertEquals(BridgeKind.STATIC_METHOD, d.kind());
    }

    @Test
    public void cooldownTimeDescriptorIsFieldKind() {
        NmsBridgeDescriptor d = descriptorById(TweakHopperIndex.BRIDGE_COOLDOWN_TIME);
        Assert.assertNotNull("cooldownTime descriptor must be present", d);
        Assert.assertEquals(BridgeKind.FIELD, d.kind());
    }

    @Test
    public void getBlockEntityDescriptorIsMethodKind() {
        NmsBridgeDescriptor d = descriptorById(TweakHopperIndex.BRIDGE_GET_BLOCK_ENTITY);
        Assert.assertNotNull("getBlockEntity descriptor must be present", d);
        Assert.assertEquals(BridgeKind.METHOD, d.kind());
    }

    @Test
    public void blockPosCtorDescriptorIsConstructorKind() {
        NmsBridgeDescriptor d = descriptorById(TweakHopperIndex.BRIDGE_BLOCK_POS_CTOR);
        Assert.assertNotNull("BlockPos constructor descriptor must be present", d);
        Assert.assertEquals(BridgeKind.CONSTRUCTOR, d.kind());
    }

    @Test
    public void allDescriptorsHaveNonEmptyClassNames() {
        for (NmsBridgeDescriptor d : TweakHopperIndex.hopperBridgeDescriptors()) {
            Assert.assertFalse(
                "Descriptor '" + d.logicalId() + "' must have at least one class name candidate",
                d.classNames().isEmpty()
            );
        }
    }

    @Test
    public void allDescriptorsHaveUniqueLogicalIds() {
        List<NmsBridgeDescriptor> descriptors = TweakHopperIndex.hopperBridgeDescriptors();
        long uniqueIds = descriptors.stream().map(NmsBridgeDescriptor::logicalId).distinct().count();
        Assert.assertEquals("All 4 descriptors must have unique logical IDs", 4, uniqueIds);
    }

    @Test
    public void allDescriptorsResolveWithoutThrowing() {
        NmsBridgeRegistry registry = new NmsBridgeRegistry();
        for (NmsBridgeDescriptor descriptor : TweakHopperIndex.hopperBridgeDescriptors()) {
            NmsBridgeHandle handle = null;
            try {
                handle = registry.resolve(descriptor);
            } catch (Throwable t) {
                Assert.fail("Resolving descriptor '" + descriptor.logicalId() + "' must not throw: " + t);
            }
            Assert.assertNotNull("Handle for '" + descriptor.logicalId() + "' must not be null", handle);
        }
    }

    @Test
    public void unavailableHandlesHaveFailureReasons() {
        NmsBridgeRegistry registry = new NmsBridgeRegistry();
        for (NmsBridgeDescriptor descriptor : TweakHopperIndex.hopperBridgeDescriptors()) {
            NmsBridgeHandle handle = registry.resolve(descriptor);
            if (!handle.available()) {
                Assert.assertNotNull(
                    "Unavailable handle for '" + descriptor.logicalId() + "' must have a failure reason",
                    handle.resolution().failureReason()
                );
                Assert.assertFalse(
                    "Unavailable handle for '" + descriptor.logicalId() + "' must have non-empty failure reason",
                    handle.resolution().failureReason().isEmpty()
                );
            }
        }
    }

    @Test
    public void suckInItemsInvocationsCounterStartsAtZero() {
        Assert.assertEquals(0L, FeatureHopperContainerThroughputMap.suckInItemsInvocations.get());
    }

    @Test
    public void suckInItemsInvocationsCounterIsAtomicallyIncrementable() {
        long before = FeatureHopperContainerThroughputMap.suckInItemsInvocations.get();
        FeatureHopperContainerThroughputMap.suckInItemsInvocations.incrementAndGet();
        long after = FeatureHopperContainerThroughputMap.suckInItemsInvocations.get();
        Assert.assertEquals(before + 1, after);
        FeatureHopperContainerThroughputMap.suckInItemsInvocations.addAndGet(-(after - 0));
    }

    @Test
    public void nmsDescriptorsHaveMappingsKeys() {
        for (String id : List.of(
            TweakHopperIndex.BRIDGE_ADD_ITEM,
            TweakHopperIndex.BRIDGE_COOLDOWN_TIME,
            TweakHopperIndex.BRIDGE_GET_BLOCK_ENTITY
        )) {
            NmsBridgeDescriptor d = descriptorById(id);
            Assert.assertNotNull("Descriptor '" + id + "' must be present", d);
            Assert.assertTrue(
                "Descriptor '" + id + "' must have a mappings key",
                d.mappingsKey().isPresent()
            );
            Assert.assertFalse(
                "Descriptor '" + id + "' mappings key must be non-empty",
                d.mappingsKey().get().isEmpty()
            );
        }
    }

    private NmsBridgeDescriptor descriptorById(String id) {
        return TweakHopperIndex.hopperBridgeDescriptors().stream()
            .filter(d -> d.logicalId().equals(id))
            .findFirst()
            .orElse(null);
    }
}
