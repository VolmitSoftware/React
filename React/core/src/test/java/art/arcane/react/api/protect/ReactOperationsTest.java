package art.arcane.react.api.protect;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

class ReactOperationsTest {

  @Test
  void theVocabularyIsExactlyTheOperationsReactConsults() {
    Assertions.assertEquals(
        Set.of(ReactOperation.STACK, ReactOperation.TRIM, ReactOperation.PURGE,
            ReactOperation.SLEEP, ReactOperation.DESPAWN, ReactOperation.SPAWN_CAP),
        Set.of(ReactOperation.values()));
    Assertions.assertEquals(0b111111, ReactOperations.all());
  }

  @Test
  void noneCoversNothing() {
    for (ReactOperation operation : ReactOperation.values()) {
      Assertions.assertFalse(ReactOperations.covers(ReactOperations.NONE, operation));
    }
  }

  @Test
  void allCoversEveryOperation() {
    for (ReactOperation operation : ReactOperation.values()) {
      Assertions.assertTrue(ReactOperations.covers(ReactOperations.all(), operation));
    }
  }

  @Test
  void ofCoversOnlyTheNamedOperations() {
    int mask = ReactOperations.of(ReactOperation.STACK, ReactOperation.SLEEP);
    Assertions.assertTrue(ReactOperations.covers(mask, ReactOperation.STACK));
    Assertions.assertTrue(ReactOperations.covers(mask, ReactOperation.SLEEP));
    Assertions.assertFalse(ReactOperations.covers(mask, ReactOperation.TRIM));
    Assertions.assertFalse(ReactOperations.covers(mask, ReactOperation.PURGE));
  }

  @Test
  void ofIgnoresNullElementsAndNullArrays() {
    Assertions.assertEquals(ReactOperations.NONE, ReactOperations.of((ReactOperation[]) null));
    Assertions.assertEquals(
        ReactOperations.of(ReactOperation.TRIM),
        ReactOperations.of(null, ReactOperation.TRIM, null));
  }

  @Test
  void coversIsFalseForNullOperation() {
    Assertions.assertFalse(ReactOperations.covers(ReactOperations.all(), null));
  }

  @Test
  void expandRoundTripsThroughOf() {
    int mask = ReactOperations.of(ReactOperation.PURGE, ReactOperation.SLEEP, ReactOperation.SPAWN_CAP);
    Set<ReactOperation> expanded = ReactOperations.expand(mask);
    Assertions.assertEquals(
        Set.of(ReactOperation.PURGE, ReactOperation.SLEEP, ReactOperation.SPAWN_CAP),
        expanded);
    Assertions.assertEquals(mask, ReactOperations.of(expanded));
  }

  @Test
  void expandOfNoneIsEmpty() {
    Assertions.assertTrue(ReactOperations.expand(ReactOperations.NONE).isEmpty());
  }

  @Test
  void sanitizeDropsBitsOutsideTheEnum() {
    Assertions.assertEquals(ReactOperations.all(), ReactOperations.sanitize(-1));
    Assertions.assertEquals(ReactOperations.NONE, ReactOperations.sanitize(1 << 30));
  }

  @Property(tries = 200)
  void sanitizeThenExpandThenOfIsIdempotent(@ForAll @IntRange(min = Integer.MIN_VALUE, max = Integer.MAX_VALUE) int raw) {
    int mask = ReactOperations.sanitize(raw);
    Assertions.assertEquals(mask, ReactOperations.of(ReactOperations.expand(mask)));
  }
}
