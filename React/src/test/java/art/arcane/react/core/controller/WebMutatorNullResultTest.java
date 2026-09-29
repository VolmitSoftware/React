package art.arcane.react.core.controller;

import art.arcane.react.util.common.scheduling.J;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.function.Supplier;

class WebMutatorNullResultTest {
  @Test
  void unconfirmedConfigWriteIsReportedAsRejected() {
    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.sResult(Mockito.<Supplier<Object>>any())).thenReturn(null);

      Assertions.assertFalse(WebController.applyConfigValueOnServerThread("core.example", true));
    }
  }

  @Test
  void unconfirmedPresetIsReportedAsFailed() {
    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.sResult(Mockito.<Supplier<Object>>any())).thenReturn(null);

      Assertions.assertEquals(-1, WebController.applyPresetOnServerThread("balanced"));
    }
  }

  @Test
  void confirmedResultsPassThrough() {
    try (MockedStatic<J> scheduling = Mockito.mockStatic(J.class)) {
      scheduling.when(() -> J.sResult(Mockito.<Supplier<Object>>any())).thenReturn(true, 7);

      Assertions.assertTrue(WebController.applyConfigValueOnServerThread("core.example", true));
      Assertions.assertEquals(7, WebController.applyPresetOnServerThread("balanced"));
    }
  }
}
