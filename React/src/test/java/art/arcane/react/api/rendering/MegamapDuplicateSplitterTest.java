package art.arcane.react.api.rendering;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MegamapDuplicateSplitterTest {

    private static final UUID LOW = new UUID(0L, 1L);
    private static final UUID HIGH = new UUID(0L, 2L);

    private MegamapDuplicateSplitter.DuplicateFrame frame(UUID frameId, int mapId, long firstSeenMs) {
        return new MegamapDuplicateSplitter.DuplicateFrame(frameId, mapId, "tps", firstSeenMs);
    }

    private MegamapDuplicateSplitter.DuplicateFrame frame(UUID frameId, int mapId, String rendererId, long firstSeenMs) {
        return new MegamapDuplicateSplitter.DuplicateFrame(frameId, mapId, rendererId, firstSeenMs);
    }

    @Test
    public void nullFramesProduceEmptyPlan() {
        Assertions.assertTrue(MegamapDuplicateSplitter.plan(null).isEmpty());
    }

    @Test
    public void emptyFramesProduceEmptyPlan() {
        Assertions.assertTrue(MegamapDuplicateSplitter.plan(List.of()).isEmpty());
    }

    @Test
    public void singleFrameIsNeverSplit() {
        Assertions.assertTrue(MegamapDuplicateSplitter.plan(List.of(frame(LOW, 7, 100L))).isEmpty());
    }

    @Test
    public void distinctMapIdsAreNeverSplit() {
        Set<UUID> plan = MegamapDuplicateSplitter.plan(List.of(
                frame(LOW, 7, 100L),
                frame(HIGH, 8, 100L),
                frame(UUID.randomUUID(), 9, 100L)
        ));

        Assertions.assertTrue(plan.isEmpty());
    }

    @Test
    public void duplicatePairKeepsOldestAndSplitsTheOther() {
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();

        Set<UUID> plan = MegamapDuplicateSplitter.plan(List.of(
                frame(newer, 7, 900L),
                frame(older, 7, 100L)
        ));

        Assertions.assertEquals(Set.of(newer), plan);
    }

    @Test
    public void firstSeenTieIsBrokenByAscendingUuid() {
        Assertions.assertTrue(LOW.compareTo(HIGH) < 0);

        Set<UUID> plan = MegamapDuplicateSplitter.plan(List.of(
                frame(HIGH, 7, 500L),
                frame(LOW, 7, 500L)
        ));

        Assertions.assertEquals(Set.of(HIGH), plan);
    }

    @Test
    public void duplicateGroupingIgnoresRendererId() {
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();

        Set<UUID> plan = MegamapDuplicateSplitter.plan(List.of(
                frame(older, 7, "tps", 100L),
                frame(newer, 7, "tick-time", 200L)
        ));

        Assertions.assertEquals(Set.of(newer), plan);
    }

    @Test
    public void zeroMapIdIsAValidGroup() {
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();

        Set<UUID> plan = MegamapDuplicateSplitter.plan(List.of(
                frame(older, 0, 100L),
                frame(newer, 0, 200L)
        ));

        Assertions.assertEquals(Set.of(newer), plan);
    }

    @Test
    public void blankRendererFramesAreIgnoredSoTheGroupCollapses() {
        UUID kept = UUID.randomUUID();

        Set<UUID> plan = MegamapDuplicateSplitter.plan(List.of(
                frame(kept, 7, "tps", 100L),
                frame(UUID.randomUUID(), 7, "   ", 200L)
        ));

        Assertions.assertTrue(plan.isEmpty());
    }

    @Test
    public void emptyRendererFramesAreIgnoredSoTheGroupCollapses() {
        Set<UUID> plan = MegamapDuplicateSplitter.plan(List.of(
                frame(UUID.randomUUID(), 7, "", 100L),
                frame(UUID.randomUUID(), 7, "tps", 200L)
        ));

        Assertions.assertTrue(plan.isEmpty());
    }

    @Test
    public void nullRendererFramesAreIgnoredSoTheGroupCollapses() {
        Set<UUID> plan = MegamapDuplicateSplitter.plan(List.of(
                frame(UUID.randomUUID(), 7, null, 100L),
                frame(UUID.randomUUID(), 7, "tps", 200L)
        ));

        Assertions.assertTrue(plan.isEmpty());
    }

    @Test
    public void negativeMapIdFramesAreIgnoredSoTheGroupCollapses() {
        Set<UUID> plan = MegamapDuplicateSplitter.plan(List.of(
                frame(UUID.randomUUID(), -1, 100L),
                frame(UUID.randomUUID(), -1, 200L)
        ));

        Assertions.assertTrue(plan.isEmpty());
    }

    @Test
    public void nullFrameIdFramesAreIgnoredSoTheGroupCollapses() {
        Set<UUID> plan = MegamapDuplicateSplitter.plan(List.of(
                frame(null, 7, 100L),
                frame(UUID.randomUUID(), 7, 200L)
        ));

        Assertions.assertTrue(plan.isEmpty());
    }

    @Test
    public void nullEntriesAreIgnored() {
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        List<MegamapDuplicateSplitter.DuplicateFrame> frames = new ArrayList<>();
        frames.add(null);
        frames.add(frame(newer, 7, 300L));
        frames.add(null);
        frames.add(frame(older, 7, 100L));

        Assertions.assertEquals(Set.of(newer), MegamapDuplicateSplitter.plan(frames));
    }

    @Test
    public void tripleDuplicateSplitsEveryFrameButTheOldest() {
        UUID oldest = UUID.randomUUID();
        UUID middle = UUID.randomUUID();
        UUID newest = UUID.randomUUID();

        Set<UUID> plan = MegamapDuplicateSplitter.plan(List.of(
                frame(middle, 7, 200L),
                frame(newest, 7, 300L),
                frame(oldest, 7, 100L)
        ));

        Assertions.assertEquals(Set.of(middle, newest), plan);
    }

    @Test
    public void independentDuplicateGroupsAreSplitPerGroup() {
        UUID keptSeven = UUID.randomUUID();
        UUID splitSeven = UUID.randomUUID();
        UUID keptNine = UUID.randomUUID();
        UUID splitNine = UUID.randomUUID();

        Set<UUID> plan = MegamapDuplicateSplitter.plan(List.of(
                frame(keptSeven, 7, 100L),
                frame(splitSeven, 7, 200L),
                frame(splitNine, 9, 800L),
                frame(keptNine, 9, 400L),
                frame(UUID.randomUUID(), 11, 50L)
        ));

        Assertions.assertEquals(Set.of(splitSeven, splitNine), plan);
    }

    @Test
    public void planIsDeterministicAcrossRepeatedCalls() {
        List<MegamapDuplicateSplitter.DuplicateFrame> frames = List.of(
                frame(UUID.randomUUID(), 7, 100L),
                frame(UUID.randomUUID(), 7, 200L),
                frame(LOW, 9, 500L),
                frame(HIGH, 9, 500L)
        );

        Assertions.assertEquals(MegamapDuplicateSplitter.plan(frames), MegamapDuplicateSplitter.plan(frames));
    }

    @Test
    public void planIsIndependentOfInputOrder() {
        List<MegamapDuplicateSplitter.DuplicateFrame> frames = List.of(
                frame(UUID.randomUUID(), 7, 100L),
                frame(UUID.randomUUID(), 7, 200L),
                frame(UUID.randomUUID(), 7, 300L),
                frame(LOW, 9, 500L),
                frame(HIGH, 9, 500L)
        );
        List<MegamapDuplicateSplitter.DuplicateFrame> reversed = new ArrayList<>(frames);
        Collections.reverse(reversed);

        Assertions.assertEquals(MegamapDuplicateSplitter.plan(frames), MegamapDuplicateSplitter.plan(reversed));
        Assertions.assertEquals(3, MegamapDuplicateSplitter.plan(reversed).size());
    }
}
