package art.arcane.react.core.history;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DoubleColumnCodecTest {
  @Test
  void roundTripsFiniteDoubleBitsAndGapsExactly() throws Exception {
    Random random = new Random(0x5EEDBEEFL);
    double[] values = new double[20_000];
    long[] counts = new long[values.length];
    for (int index = 0; index < values.length; index++) {
      long bits;
      double value;
      do {
        bits = random.nextLong();
        value = Double.longBitsToDouble(bits);
      } while (!Double.isFinite(value));
      values[index] = value;
      counts[index] = random.nextInt(5) == 0 ? 0L : 1L + random.nextInt(900);
    }

    DoubleColumnCodec.EncodedColumn encoded = DoubleColumnCodec.encode(values, counts);
    double[] decoded = DoubleColumnCodec.decode(encoded, counts);

    for (int index = 0; index < values.length; index++) {
      if (counts[index] > 0L) {
        assertEquals(
            Double.doubleToRawLongBits(values[index]),
            Double.doubleToRawLongBits(decoded[index]),
            "raw bits differ at index " + index
        );
      }
    }
  }

  @Test
  void preservesNegativeZero() throws Exception {
    double[] values = new double[]{-0D, -0D, 0D};
    long[] counts = new long[]{1L, 1L, 1L};

    double[] decoded = DoubleColumnCodec.decode(DoubleColumnCodec.encode(values, counts), counts);

    assertEquals(Double.doubleToRawLongBits(-0D), Double.doubleToRawLongBits(decoded[0]));
    assertEquals(Double.doubleToRawLongBits(-0D), Double.doubleToRawLongBits(decoded[1]));
    assertEquals(Double.doubleToRawLongBits(0D), Double.doubleToRawLongBits(decoded[2]));
  }

  @Test
  void compressesConstantColumnsToOneDouble() throws Exception {
    double[] values = new double[900];
    long[] counts = new long[900];
    java.util.Arrays.fill(values, 20D);
    java.util.Arrays.fill(counts, 1L);

    DoubleColumnCodec.EncodedColumn encoded = DoubleColumnCodec.encode(values, counts);

    assertEquals(0, encoded.codec());
    assertEquals(Long.BYTES, encoded.data().length);
  }
}
