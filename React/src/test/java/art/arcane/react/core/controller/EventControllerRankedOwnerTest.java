package art.arcane.react.core.controller;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EventControllerRankedOwnerTest {
  @Test
  void rankedOwnerSkipsReactItself() {
    Assertions.assertFalse(EventController.rankedOwner("React", "React"));
  }

  @Test
  void rankedOwnerKeepsOtherPlugins() {
    Assertions.assertTrue(EventController.rankedOwner("Iris", "React"));
  }

  @Test
  void rankedOwnerKeepsEveryoneWithoutReactInstance() {
    Assertions.assertTrue(EventController.rankedOwner("React", null));
  }

  @Test
  void rankedOwnerSkipsMissingOwner() {
    Assertions.assertFalse(EventController.rankedOwner(null, "React"));
  }
}
