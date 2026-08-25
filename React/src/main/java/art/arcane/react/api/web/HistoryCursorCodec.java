package art.arcane.react.api.web;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.CRC32C;

public final class HistoryCursorCodec {
  private static final int VERSION = 1;
  private static final int MAX_CURSOR_BYTES = 16 * 1024;
  private static final int MAX_IDS = 64;
  private static final int MAX_ID_BYTES = 512;

  private HistoryCursorCodec() {
  }

  public static String encode(HistoryCursor cursor) {
    try {
      ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
      DataOutputStream body = new DataOutputStream(bodyBytes);
      body.writeByte(VERSION);
      body.writeLong(cursor.requestedFromMs());
      body.writeLong(cursor.requestedToMs());
      body.writeLong(cursor.nextFromMs());
      body.writeLong(cursor.resolutionMs());
      body.writeLong(cursor.throughSequence());
      body.writeLong(cursor.throughMs());
      body.writeInt(cursor.pagePoints());
      body.writeInt(cursor.ids().size());
      for (String id : cursor.ids()) {
        writeString(body, id);
      }
      body.flush();
      byte[] payload = bodyBytes.toByteArray();
      CRC32C crc = new CRC32C();
      crc.update(payload, 0, payload.length);
      ByteArrayOutputStream framedBytes = new ByteArrayOutputStream(payload.length + Integer.BYTES);
      DataOutputStream framed = new DataOutputStream(framedBytes);
      framed.write(payload);
      framed.writeInt((int) crc.getValue());
      framed.flush();
      return Base64.getUrlEncoder().withoutPadding().encodeToString(framedBytes.toByteArray());
    } catch (IOException failure) {
      throw new IllegalStateException("Failed to encode metric history cursor", failure);
    }
  }

  public static HistoryCursor decode(String encoded) throws IOException {
    byte[] framed;
    try {
      framed = Base64.getUrlDecoder().decode(encoded);
    } catch (IllegalArgumentException failure) {
      throw new IOException("History cursor is not valid base64url", failure);
    }
    if (framed.length <= Integer.BYTES || framed.length > MAX_CURSOR_BYTES) {
      throw new IOException("History cursor has an invalid length");
    }
    int payloadLength = framed.length - Integer.BYTES;
    byte[] payload = java.util.Arrays.copyOf(framed, payloadLength);
    DataInputStream checksumInput = new DataInputStream(
        new ByteArrayInputStream(framed, payloadLength, Integer.BYTES)
    );
    int expectedChecksum = checksumInput.readInt();
    CRC32C crc = new CRC32C();
    crc.update(payload, 0, payload.length);
    if ((int) crc.getValue() != expectedChecksum) {
      throw new IOException("History cursor checksum does not match");
    }
    DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
    int version = input.readUnsignedByte();
    if (version != VERSION) {
      throw new IOException("Unsupported history cursor version " + version);
    }
    long requestedFromMs = input.readLong();
    long requestedToMs = input.readLong();
    long nextFromMs = input.readLong();
    long resolutionMs = input.readLong();
    long throughSequence = input.readLong();
    long throughMs = input.readLong();
    int pagePoints = input.readInt();
    int idCount = input.readInt();
    if (idCount < 1 || idCount > MAX_IDS) {
      throw new IOException("History cursor has an invalid metric count");
    }
    List<String> ids = new ArrayList<>(idCount);
    for (int index = 0; index < idCount; index++) {
      ids.add(readString(input));
    }
    if (input.available() != 0) {
      throw new IOException("History cursor contains trailing bytes");
    }
    return new HistoryCursor(
        List.copyOf(ids),
        requestedFromMs,
        requestedToMs,
        nextFromMs,
        resolutionMs,
        throughSequence,
        throughMs,
        pagePoints
    );
  }

  private static void writeString(DataOutputStream output, String value) throws IOException {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > MAX_ID_BYTES) {
      throw new IOException("Metric id is too long for a history cursor");
    }
    output.writeShort(bytes.length);
    output.write(bytes);
  }

  private static String readString(DataInputStream input) throws IOException {
    int length = input.readUnsignedShort();
    if (length > MAX_ID_BYTES) {
      throw new IOException("Metric id is too long in a history cursor");
    }
    byte[] bytes = input.readNBytes(length);
    if (bytes.length != length) {
      throw new EOFException("Metric id is truncated in a history cursor");
    }
    return new String(bytes, StandardCharsets.UTF_8);
  }

  public record HistoryCursor(
      List<String> ids,
      long requestedFromMs,
      long requestedToMs,
      long nextFromMs,
      long resolutionMs,
      long throughSequence,
      long throughMs,
      int pagePoints
  ) {
  }
}
