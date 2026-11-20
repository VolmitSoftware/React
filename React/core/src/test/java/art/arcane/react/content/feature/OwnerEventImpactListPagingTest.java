package art.arcane.react.content.feature;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OwnerEventImpactListPagingTest {
  @Test
  void aListThatFitsNeverTurns() {
    Assertions.assertEquals(1, OwnerEventImpactListRenderer.pageCount(9, 9));
    Assertions.assertEquals(0, OwnerEventImpactListRenderer.page(9, 9, 6, 999_999L));
    Assertions.assertEquals(0, OwnerEventImpactListRenderer.page(0, 9, 6, 999_999L));
  }

  @Test
  void pagesTurnOnTheIntervalAndWrap() {
    Assertions.assertEquals(4, OwnerEventImpactListRenderer.pageCount(31, 8));
    Assertions.assertEquals(0, OwnerEventImpactListRenderer.page(31, 8, 6, 5_999L));
    Assertions.assertEquals(1, OwnerEventImpactListRenderer.page(31, 8, 6, 6_000L));
    Assertions.assertEquals(3, OwnerEventImpactListRenderer.page(31, 8, 6, 18_000L));
    Assertions.assertEquals(0, OwnerEventImpactListRenderer.page(31, 8, 6, 24_000L));
  }

  @Test
  void zeroSecondsPinsTheFirstPage() {
    Assertions.assertEquals(0, OwnerEventImpactListRenderer.page(300, 8, 0, 60_000L));
  }
}
