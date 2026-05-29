package art.arcane.react.content.tweak;

import art.arcane.react.core.bridge.BridgeKind;
import art.arcane.react.core.bridge.NmsBridgeDescriptor;
import art.arcane.react.core.bridge.NmsBridgeHandle;
import art.arcane.react.core.bridge.NmsBridgeRegistry;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TweakFastFluidsParityTest {

    @Test
    public void allSevenBridgeIdConstantsExist() {
        Assert.assertNotNull(TweakFastFluids.BRIDGE_GET_FLUID_STATE);
        Assert.assertNotNull(TweakFastFluids.BRIDGE_BLOCK_POS_CTOR);
        Assert.assertNotNull(TweakFastFluids.BRIDGE_IS_EMPTY);
        Assert.assertNotNull(TweakFastFluids.BRIDGE_GET_TYPE);
        Assert.assertNotNull(TweakFastFluids.BRIDGE_WORLD_TICK_FLUID);
        Assert.assertNotNull(TweakFastFluids.BRIDGE_FLUID_TYPE_TICK);
        Assert.assertNotNull(TweakFastFluids.BRIDGE_FLUID_STATE_TICK);
        Assert.assertFalse(TweakFastFluids.BRIDGE_GET_FLUID_STATE.isEmpty());
        Assert.assertFalse(TweakFastFluids.BRIDGE_BLOCK_POS_CTOR.isEmpty());
        Assert.assertFalse(TweakFastFluids.BRIDGE_IS_EMPTY.isEmpty());
        Assert.assertFalse(TweakFastFluids.BRIDGE_GET_TYPE.isEmpty());
        Assert.assertFalse(TweakFastFluids.BRIDGE_WORLD_TICK_FLUID.isEmpty());
        Assert.assertFalse(TweakFastFluids.BRIDGE_FLUID_TYPE_TICK.isEmpty());
        Assert.assertFalse(TweakFastFluids.BRIDGE_FLUID_STATE_TICK.isEmpty());
    }

    @Test
    public void fluidBridgeDescriptorsReturnsSevenEntries() {
        List<NmsBridgeDescriptor> descriptors = TweakFastFluids.fluidBridgeDescriptors();
        Assert.assertEquals(7, descriptors.size());
    }

    @Test
    public void waterAndLavaAccelerationAreEnabledByDefault() {
        TweakFastFluids fastFluids = new TweakFastFluids();
        Assert.assertTrue(fastFluids.isAccelerateWater());
        Assert.assertTrue(fastFluids.isAccelerateLava());
    }

    @Test
    public void blockPosDescriptorIsConstructorKind() {
        List<NmsBridgeDescriptor> descriptors = TweakFastFluids.fluidBridgeDescriptors();
        NmsBridgeDescriptor blockPosCtor = descriptors.stream()
            .filter(d -> d.logicalId().equals(TweakFastFluids.BRIDGE_BLOCK_POS_CTOR))
            .findFirst()
            .orElse(null);
        Assert.assertNotNull("BlockPos constructor descriptor must be present", blockPosCtor);
        Assert.assertEquals(BridgeKind.CONSTRUCTOR, blockPosCtor.kind());
    }

    @Test
    public void methodDescriptorsAreMethodKind() {
        List<NmsBridgeDescriptor> descriptors = TweakFastFluids.fluidBridgeDescriptors();
        List<String> methodIds = List.of(
            TweakFastFluids.BRIDGE_GET_FLUID_STATE,
            TweakFastFluids.BRIDGE_IS_EMPTY,
            TweakFastFluids.BRIDGE_GET_TYPE,
            TweakFastFluids.BRIDGE_WORLD_TICK_FLUID,
            TweakFastFluids.BRIDGE_FLUID_TYPE_TICK,
            TweakFastFluids.BRIDGE_FLUID_STATE_TICK
        );
        for (String id : methodIds) {
            NmsBridgeDescriptor d = descriptors.stream()
                .filter(desc -> desc.logicalId().equals(id))
                .findFirst()
                .orElse(null);
            Assert.assertNotNull("Descriptor for '" + id + "' must be present", d);
            Assert.assertEquals("Descriptor '" + id + "' must be METHOD kind", BridgeKind.METHOD, d.kind());
        }
    }

    @Test
    public void allDescriptorsHaveNonEmptyClassNames() {
        for (NmsBridgeDescriptor d : TweakFastFluids.fluidBridgeDescriptors()) {
            Assert.assertFalse(
                "Descriptor '" + d.logicalId() + "' must have at least one class name candidate",
                d.classNames().isEmpty()
            );
        }
    }

    @Test
    public void allDescriptorsHaveUniqueLogicalIds() {
        List<NmsBridgeDescriptor> descriptors = TweakFastFluids.fluidBridgeDescriptors();
        long uniqueIds = descriptors.stream().map(NmsBridgeDescriptor::logicalId).distinct().count();
        Assert.assertEquals("All 7 descriptors must have unique logical IDs", 7, uniqueIds);
    }

    @Test
    public void allDescriptorsResolveWithoutThrowing() {
        NmsBridgeRegistry registry = new NmsBridgeRegistry();
        for (NmsBridgeDescriptor descriptor : TweakFastFluids.fluidBridgeDescriptors()) {
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
        for (NmsBridgeDescriptor descriptor : TweakFastFluids.fluidBridgeDescriptors()) {
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
}
