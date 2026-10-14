package art.arcane.react.core.history;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

public final class VersionOneHistoryFixtures {
  private static final String RAW_SEGMENT =
      "524854530001000000000000000000000003840000000253455249000000086172636869766564000000084172636869766564"
          + "000000056279746573000000050000000501000003c900000025ae1bba4d785e6360606e6100014630390a4643603404062004"
          + "5880767238c8426c268703004ac9029653455249000000037470730000000354505300000000000000010000000201000003f1"
          + "0000002833e09f4e785e6360606e616064641805a321301a020317022c40ab051c4c200e703086d0b410040027890571454e4421";
  private static final String ROLLUP_SEGMENT =
      "524854530001010000000000000000000004380000000153455249000000097469636b2d74696d65000000095469636b2054696d65"
          + "000000026d73000000000000000201000004a600000046a8d57304785e636060b160666862641805a321301a02a321404a08b000"
          + "150b38a840b4d87f80d0104111a8e00364411308c7c1c50149a563034470730792204ca5730258100033840938454e4421";
  private static final String WAL =
      "5248574c0001000000170100000007000000037470730000000354505300000000e63ac113000000210200000000000000290000"
          + "0000000003e800000001000000074034000000000000b73e27d50000002102000000000000002a00000000000004b000000001"
          + "0000000740320000000000003a01b63a0000002102000000000000002b00000000000007d0000000010000000740330000000000"
          + "000e34c86e";

  private VersionOneHistoryFixtures() {
  }

  public static Path writeRawSegment(Path directory) throws IOException {
    return write(directory.resolve("raw/0.rht"), RAW_SEGMENT);
  }

  public static Path writeRollupSegment(Path directory) throws IOException {
    return write(directory.resolve("10s/0.rht"), ROLLUP_SEGMENT);
  }

  public static Path writeWal(Path directory) throws IOException {
    return write(directory.resolve("active.wal"), WAL);
  }

  private static Path write(Path path, String hex) throws IOException {
    Files.createDirectories(path.getParent());
    return Files.write(path, HexFormat.of().parseHex(hex));
  }
}
