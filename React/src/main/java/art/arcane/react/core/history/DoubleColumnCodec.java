package art.arcane.react.core.history;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

final class DoubleColumnCodec {
  private static final int CONSTANT = 0;
  private static final int XOR = 1;
  private static final int INTEGRAL_DELTA = 2;
  private static final int RUN_LENGTH = 3;
  private static final int RAW = 4;

  private DoubleColumnCodec() {
  }

  static EncodedColumn encode(double[] values, long[] counts) throws IOException {
    long[] bits = presentBits(values, counts);
    if (bits.length == 0) {
      return new EncodedColumn(CONSTANT, new byte[0]);
    }

    EncodedColumn best = raw(bits);
    EncodedColumn xor = xor(bits);
    if (xor.data().length < best.data().length) {
      best = xor;
    }
    EncodedColumn runs = runLength(bits);
    if (runs.data().length < best.data().length) {
      best = runs;
    }
    EncodedColumn integral = integralDelta(bits);
    if (integral != null && integral.data().length < best.data().length) {
      best = integral;
    }
    if (constant(bits)) {
      return new EncodedColumn(CONSTANT, longBytes(bits[0]));
    }
    return best;
  }

  static double[] decode(EncodedColumn encoded, long[] counts) throws IOException {
    int present = 0;
    for (long count : counts) {
      if (count > 0L) {
        present++;
      }
    }
    long[] bits = switch (encoded.codec()) {
      case CONSTANT -> decodeConstant(encoded.data(), present);
      case XOR -> decodeXor(encoded.data(), present);
      case INTEGRAL_DELTA -> decodeIntegralDelta(encoded.data(), present);
      case RUN_LENGTH -> decodeRunLength(encoded.data(), present);
      case RAW -> decodeRaw(encoded.data(), present);
      default -> throw new IOException("Unknown history column codec " + encoded.codec());
    };
    double[] values = new double[counts.length];
    int source = 0;
    for (int index = 0; index < counts.length; index++) {
      if (counts[index] > 0L) {
        if (source >= bits.length) {
          throw new EOFException("History column ended before all present values were decoded");
        }
        values[index] = Double.longBitsToDouble(bits[source++]);
      }
    }
    if (source != bits.length) {
      throw new IOException("History column contains more values than its presence stream");
    }
    return values;
  }

  private static long[] presentBits(double[] values, long[] counts) {
    int present = 0;
    for (long count : counts) {
      if (count > 0L) {
        present++;
      }
    }
    long[] bits = new long[present];
    int target = 0;
    for (int index = 0; index < counts.length; index++) {
      if (counts[index] > 0L) {
        bits[target++] = Double.doubleToRawLongBits(values[index]);
      }
    }
    return bits;
  }

  private static EncodedColumn raw(long[] bits) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(bits.length * Long.BYTES);
    DataOutputStream output = new DataOutputStream(bytes);
    for (long value : bits) {
      output.writeLong(value);
    }
    return new EncodedColumn(RAW, bytes.toByteArray());
  }

  private static EncodedColumn xor(long[] bits) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(Long.BYTES, bits.length * 3));
    DataOutputStream output = new DataOutputStream(bytes);
    output.writeLong(bits[0]);
    long previous = bits[0];
    for (int index = 1; index < bits.length; index++) {
      long current = bits[index];
      writeUnsignedVarLong(output, current ^ previous);
      previous = current;
    }
    return new EncodedColumn(XOR, bytes.toByteArray());
  }

  private static EncodedColumn integralDelta(long[] bits) throws IOException {
    long[] values = new long[bits.length];
    for (int index = 0; index < bits.length; index++) {
      double value = Double.longBitsToDouble(bits[index]);
      if (!Double.isFinite(value) || value < Long.MIN_VALUE || value > Long.MAX_VALUE) {
        return null;
      }
      long integral = (long) value;
      if (Double.doubleToRawLongBits((double) integral) != bits[index]) {
        return null;
      }
      values[index] = integral;
    }

    ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(1, values.length * 2));
    DataOutputStream output = new DataOutputStream(bytes);
    writeSignedVarLong(output, values[0]);
    long previous = values[0];
    for (int index = 1; index < values.length; index++) {
      long current = values[index];
      writeSignedVarLong(output, current - previous);
      previous = current;
    }
    return new EncodedColumn(INTEGRAL_DELTA, bytes.toByteArray());
  }

  private static EncodedColumn runLength(long[] bits) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(Long.BYTES, bits.length * 2));
    DataOutputStream output = new DataOutputStream(bytes);
    int start = 0;
    while (start < bits.length) {
      int end = start + 1;
      while (end < bits.length && bits[end] == bits[start]) {
        end++;
      }
      writeUnsignedVarLong(output, end - start);
      output.writeLong(bits[start]);
      start = end;
    }
    return new EncodedColumn(RUN_LENGTH, bytes.toByteArray());
  }

  private static boolean constant(long[] bits) {
    long first = bits[0];
    for (int index = 1; index < bits.length; index++) {
      if (bits[index] != first) {
        return false;
      }
    }
    return true;
  }

  private static byte[] longBytes(long value) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(Long.BYTES);
    DataOutputStream output = new DataOutputStream(bytes);
    output.writeLong(value);
    return bytes.toByteArray();
  }

  private static long[] decodeConstant(byte[] data, int count) throws IOException {
    if (count == 0) {
      if (data.length != 0) {
        throw new IOException("Empty history column contains constant bytes");
      }
      return new long[0];
    }
    if (data.length != Long.BYTES) {
      throw new IOException("Invalid constant history column length " + data.length);
    }
    DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));
    long value = input.readLong();
    long[] decoded = new long[count];
    java.util.Arrays.fill(decoded, value);
    return decoded;
  }

  private static long[] decodeXor(byte[] data, int count) throws IOException {
    if (count == 0) {
      return requireEmpty(data);
    }
    DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));
    long[] decoded = new long[count];
    decoded[0] = input.readLong();
    for (int index = 1; index < count; index++) {
      decoded[index] = decoded[index - 1] ^ readUnsignedVarLong(input);
    }
    requireConsumed(input);
    return decoded;
  }

  private static long[] decodeIntegralDelta(byte[] data, int count) throws IOException {
    if (count == 0) {
      return requireEmpty(data);
    }
    DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));
    long[] decoded = new long[count];
    long current = readSignedVarLong(input);
    decoded[0] = Double.doubleToRawLongBits((double) current);
    for (int index = 1; index < count; index++) {
      current += readSignedVarLong(input);
      decoded[index] = Double.doubleToRawLongBits((double) current);
    }
    requireConsumed(input);
    return decoded;
  }

  private static long[] decodeRunLength(byte[] data, int count) throws IOException {
    if (count == 0) {
      return requireEmpty(data);
    }
    DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));
    long[] decoded = new long[count];
    int target = 0;
    while (target < count) {
      long runLength = readUnsignedVarLong(input);
      if (runLength <= 0L || runLength > count - target) {
        throw new IOException("Invalid history column run length " + runLength);
      }
      long value = input.readLong();
      int end = target + (int) runLength;
      java.util.Arrays.fill(decoded, target, end, value);
      target = end;
    }
    requireConsumed(input);
    return decoded;
  }

  private static long[] decodeRaw(byte[] data, int count) throws IOException {
    if (data.length != count * Long.BYTES) {
      throw new IOException("Invalid raw history column length " + data.length);
    }
    DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));
    long[] decoded = new long[count];
    for (int index = 0; index < count; index++) {
      decoded[index] = input.readLong();
    }
    return decoded;
  }

  static void writeUnsignedVarLong(DataOutputStream output, long value) throws IOException {
    long remaining = value;
    while ((remaining & ~0x7FL) != 0L) {
      output.writeByte((int) ((remaining & 0x7FL) | 0x80L));
      remaining >>>= 7;
    }
    output.writeByte((int) remaining);
  }

  static long readUnsignedVarLong(DataInputStream input) throws IOException {
    long value = 0L;
    int shift = 0;
    while (shift < 64) {
      int current = input.readUnsignedByte();
      value |= (long) (current & 0x7F) << shift;
      if ((current & 0x80) == 0) {
        return value;
      }
      shift += 7;
    }
    throw new IOException("History varint exceeds 64 bits");
  }

  private static void writeSignedVarLong(DataOutputStream output, long value) throws IOException {
    writeUnsignedVarLong(output, (value << 1) ^ (value >> 63));
  }

  private static long readSignedVarLong(DataInputStream input) throws IOException {
    long value = readUnsignedVarLong(input);
    return (value >>> 1) ^ -(value & 1L);
  }

  private static long[] requireEmpty(byte[] data) throws IOException {
    if (data.length != 0) {
      throw new IOException("Empty history column contains encoded bytes");
    }
    return new long[0];
  }

  private static void requireConsumed(DataInputStream input) throws IOException {
    if (input.available() != 0) {
      throw new IOException("History column contains trailing bytes");
    }
  }

  record EncodedColumn(int codec, byte[] data) {
  }
}
