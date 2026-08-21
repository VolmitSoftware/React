/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.api.benchmark;

public record DriveProfile(long payloadBytes, int chunkBytes, int randomReads, int randomReadBytes, int flushes) {
  public DriveProfile {
    if (chunkBytes <= 0) {
      throw new IllegalArgumentException("Chunk size must be positive");
    }
    if (payloadBytes < chunkBytes) {
      throw new IllegalArgumentException("Payload must cover at least one chunk");
    }
    if (randomReads <= 0 || randomReadBytes <= 0) {
      throw new IllegalArgumentException("Random read plan must be positive");
    }
    if (flushes <= 0) {
      throw new IllegalArgumentException("Flush count must be positive");
    }
  }

  public static DriveProfile standard() {
    return new DriveProfile(32L * 1024L * 1024L, 1024 * 1024, 512, 4096, 16);
  }

  public static DriveProfile quick() {
    return new DriveProfile(1024L * 1024L, 64 * 1024, 32, 4096, 4);
  }

  public int chunks() {
    return (int) (payloadBytes / chunkBytes);
  }

  public long alignedPayloadBytes() {
    return (long) chunks() * chunkBytes;
  }
}
