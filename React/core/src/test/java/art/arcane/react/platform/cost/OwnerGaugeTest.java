package art.arcane.react.platform.cost;

import art.arcane.react.api.sampler.SamplerMetadata;
import art.arcane.react.core.gui.ReactGuiTaxonomy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OwnerGaugeTest {
    @Test
    void samplerId_prefixesTheSanitizedOwner() {
        assertEquals("chunk-loader-create", OwnerGauge.CHUNK_LOADERS.samplerId("Create"));
        assertEquals("network-out-fabric-api", OwnerGauge.NETWORK_OUT.samplerId("fabric-api"));
    }

    @Test
    void forId_findsTheGaugeOfAnOwnerSampler() {
        assertEquals(OwnerGauge.PACKETS_IN, OwnerGauge.forId("packets-in-minecraft"));
        assertEquals(OwnerGauge.CHUNK_LOADERS, OwnerGauge.forId("chunk-loader-farmersdelight"));
        assertNull(OwnerGauge.forId("network-out"));
        assertNull(OwnerGauge.forId("network-send-rate"));
        assertNull(OwnerGauge.forId("chunk-tickets"));
        assertNull(OwnerGauge.forId(null));
    }

    @Test
    void metadata_usesTheGaugeUnit() {
        assertEquals("bytes/s", SamplerMetadata.forId("network-in-create", 1000L).unit());
        assertEquals("count", SamplerMetadata.forId("chunk-loader-create", 5000L).unit());
    }

    @Test
    void taxonomy_groupsByGaugeNotByOwnerName() {
        assertEquals("world", ReactGuiTaxonomy.categoryForId("chunk-loader-journeymap").key());
        assertEquals("performance", ReactGuiTaxonomy.categoryForId("network-out-farmersdelight").key());
    }
}
