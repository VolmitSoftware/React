package art.arcane.react.mod;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class RollingShareTest {
    @Test
    void foldsAverageTowardTheLatestRateAndDropOldOwners() {
        RollingShare share = new RollingShare();
        share.fold(Map.of("create", 10D));
        Assertions.assertEquals(Map.of("Create", 2000L), share.buckets(1000D, (String id) -> "Create"));
        for (int index = 0; index < 60; index++) {
            share.fold(Map.of("create", 10D));
        }
        Assertions.assertEquals(10_000L, share.buckets(1000D, (String id) -> id).get("create"), 1L);
        for (int index = 0; index < 80; index++) {
            share.fold(Map.of());
        }
        Assertions.assertTrue(share.buckets(1000D, (String id) -> id).isEmpty());
    }

    @Test
    void ownersSharingADisplayNameAddUp() {
        RollingShare share = new RollingShare();
        share.fold(Map.of("a", 5D, "b", 5D));
        Assertions.assertEquals(Map.of("Same", 2000L), share.buckets(1000D, (String id) -> "Same"));
    }
}
