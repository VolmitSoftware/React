package art.arcane.react.mod.cost;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NetworkTrafficTest {
    @BeforeEach
    void start() {
        NetworkTraffic.active(true);
        NetworkTraffic.drain(1D, (String namespace) -> namespace);
    }

    @AfterEach
    void stop() {
        NetworkTraffic.active(false);
    }

    @Test
    void drain_reportsPerSecondRatesPerOwner() {
        NetworkTraffic.sent(null, 300);
        NetworkTraffic.sent(null, 100);
        NetworkTraffic.received(null, 50);
        NetworkTraffic.sent("create", 1000);

        Map<String, NetworkTraffic.Rates> rates = NetworkTraffic.drain(2D, (String namespace) -> namespace);

        assertEquals(new NetworkTraffic.Rates(200D, 25D, 1D, 0.5D), rates.get(NetworkTraffic.MINECRAFT));
        assertEquals(new NetworkTraffic.Rates(500D, 0D, 0.5D, 0D), rates.get("create"));
    }

    @Test
    void drain_foldsNamespacesIntoTheirOwner() {
        NetworkTraffic.sent("fabric", 10);
        NetworkTraffic.received("fabric-screen-handler", 30);

        Map<String, NetworkTraffic.Rates> rates = NetworkTraffic.drain(1D,
                (String namespace) -> namespace.startsWith("fabric") ? "fabric-api" : namespace);

        assertEquals(new NetworkTraffic.Rates(10D, 30D, 1D, 1D), rates.get("fabric-api"));
    }

    @Test
    void drain_resetsCountersAndSkipsIdleChannels() {
        NetworkTraffic.sent("create", 10);
        NetworkTraffic.drain(1D, (String namespace) -> namespace);

        Map<String, NetworkTraffic.Rates> rates = NetworkTraffic.drain(1D, (String namespace) -> namespace);

        assertFalse(rates.containsKey("create"));
        assertEquals(NetworkTraffic.Rates.NONE, rates.get(NetworkTraffic.MINECRAFT));
    }

    @Test
    void sent_isIgnoredWhileInactive() {
        NetworkTraffic.active(false);
        NetworkTraffic.sent(null, 10);
        NetworkTraffic.active(true);

        assertEquals(NetworkTraffic.Rates.NONE, NetworkTraffic.drain(1D, (String namespace) -> namespace).get(NetworkTraffic.MINECRAFT));
    }
}
