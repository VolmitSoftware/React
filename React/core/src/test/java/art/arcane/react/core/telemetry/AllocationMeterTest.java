package art.arcane.react.core.telemetry;

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AllocationMeterTest {
  @Test
  void totalsContinueAcrossCollectionsAndUseElapsedTime() {
    ThreadMXBean bean = supported();
    Mockito.when(bean.getTotalThreadAllocatedBytes()).thenReturn(1_000L, 3_000L, 6_000L);
    AllocationMeter meter = new AllocationMeter(bean);
    Assertions.assertTrue(Double.isNaN(meter.sample(1_000_000_000L)));
    Assertions.assertEquals(2_000D, meter.sample(2_000_000_000L));
    Assertions.assertEquals(1_500D, meter.sample(4_000_000_000L));
  }

  @Test
  void unsupportedOrDisabledCountersStayUnavailableWithoutEnablingThem() {
    ThreadMXBean bean = Mockito.mock(ThreadMXBean.class);
    AllocationMeter meter = new AllocationMeter(bean);
    Assertions.assertTrue(Double.isNaN(meter.sample(1L)));
    Mockito.verify(bean, Mockito.never()).getTotalThreadAllocatedBytes();
    Mockito.when(bean.isThreadAllocatedMemorySupported()).thenReturn(true);
    Assertions.assertTrue(Double.isNaN(meter.sample(2L)));
    Mockito.verify(bean, Mockito.never()).setThreadAllocatedMemoryEnabled(Mockito.anyBoolean());
  }

  @Test
  void counterResetAndUnknownValuesRequireANewBaseline() {
    ThreadMXBean bean = supported();
    Mockito.when(bean.getTotalThreadAllocatedBytes()).thenReturn(5_000L, 1_000L, -1L, 2_000L, 4_000L);
    AllocationMeter meter = new AllocationMeter(bean);
    for (int second = 1; second <= 4; second++) {
      Assertions.assertTrue(Double.isNaN(meter.sample(second * 1_000_000_000L)));
    }
    Assertions.assertEquals(2_000D, meter.sample(5_000_000_000L));
  }

  private ThreadMXBean supported() {
    ThreadMXBean bean = Mockito.mock(ThreadMXBean.class);
    Mockito.when(bean.isThreadAllocatedMemorySupported()).thenReturn(true);
    Mockito.when(bean.isThreadAllocatedMemoryEnabled()).thenReturn(true);
    return bean;
  }
}
