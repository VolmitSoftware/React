/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.react.legacyutil;

import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A compressor for small JSON strings.
 *
 * <p>For very small strings, normal compression algorithms start to fail.
 * Strings shorter than 150-200 bytes might actually increase in size
 * if run through GZip or Zip compression.</p>
 *
 * <p>JsonCompress was created to increase the amount of data that could
 * be stored on NFC tags. NFC storage is so small that traditional
 * compression methods can't be used effectively.</p>
 *
 * <p>Many NFC tags come with just 144 bytes of storage. Typically, some
 * of this space will need to be reserved for overhead storage:</p>
 *
 * <ul>
 * <li>The tag id (7 bytes)</li>
 * <li>The AAR record -- if you want the tag to launch a custom
 *     Android app (about 40 bytes)</li>
 * <li>Other record headers (around 6 bytes)</li>
 * </ul>
 *
 * <p>This means that you typically have under 100 bytes of storage
 * available. This means that many developers have to switch to a
 * custom, binary format. Unfortunately, this fixes the data schema
 * in the code, which reduces flexibility.</p>
 *
 * <p>By using compressed JSON, you can get similar storage capacities to
 * custom binary formats, plus the schema is defined in the data itself.
 * This allows you to create a heterogeneous set of tags which have data
 * relevant to a context.</p>
 *
 * <p>Plus, JSON is typically far easier to deal with in code, than other
 * formats.</p>
 *
 * <p>Example code to compress a JSON string:</p>
 *
 * <pre><code>JsonCompressor compressor = new JsonCompressor();
 * String s1 = "{\n"+
 *     "   \"type\":\"record\",\n"+
 *     "   \"location\":\"in the boardroom\",\n"+
 *     "   \"filename\":\"fred.aac\",\n"+
 *     "   \"sample_rate\":\"48000\",\n"+
 *     "   \"encoding_rate\":\"320000\",\n"+
 *     "   \"audio_type\":\"aac\",\n"+
 *     "   \"stereo\":\"false\"\n"+
 *     "}\n";
 * byte[] compressedBytes = compressor.compressJson(s);</code></pre>
 *
 * <p>To expand the data, just call the expandJson method:</p>
 *
 * <code>String expanded = compressor.expandJson(compressedBytes);</code>
 *
 * @author David Griffiths
 */
public class JsonCompressor {
    private static final String escapeChar = ";";
    private final static int ESCAPED_UPPERCASE = 0x01;
    private final static int USE_PROTOTYPE = 0x04;
    String aWalk;
    int pos = 0;
    private String prototypeCompact;

    /**
     * Construct a general-purpose compressor for JSON strings.
     */
    public JsonCompressor() {
    }

    /**
     * Construct a compressor for JSON strings that are similar to the given
     * prototype {@link String}. For example, the prototype might have the
     * same JSON structure, key names and typical values.
     * <p>
     * Compressed data that is produced can only be expanded again by
     * compressors with the same prototype. Providing a prototype may produce
     * significantly greater compression.
     *
     * @param prototype An example JSON string that is similar to the strings
     *                  that will be compressed.
     */
    public JsonCompressor(String prototype) {
        if (prototype != null) {
            prototypeCompact = compact(prototype);
        }
    }

    /**
     * Compress a JSON string into a compacted byte-array.
     *
     * @param json A string in JSON format.
     * @return a compacted byte-array.
     */
    public byte[] compressJson(String json) {
        int options = 0;
        String walkFormat = walkFormat(json);
        byte[] compress;
        String tickedString = walkFormat.replaceAll("([A-Z])", escapeChar + "$1");
        String upperTickedString = tickedString.toUpperCase();
        upperTickedString = Dictionary.shorten(upperTickedString);
        byte[] compressEscapedCase = compress6AndDec(upperTickedString.getBytes());
        byte[] compressUnescapedCase = pack(walkFormat.getBytes(), 7);
        byte[] deflated = null;
        if (prototypeCompact != null) {
            deflated = deflateWithPrototype(upperTickedString);
        }
        if ((deflated != null) && (deflated.length < compressEscapedCase.length)) {
            compress = deflated;
            options = options | ESCAPED_UPPERCASE;
            options = options | USE_PROTOTYPE;
        } else if (compressEscapedCase.length < compressUnescapedCase.length) {
            compress = compressEscapedCase;
            options = options | ESCAPED_UPPERCASE;
        } else {
            compress = compressUnescapedCase;
        }
        byte[] bytesWithOptions = new byte[compress.length + 1];
        bytesWithOptions[0] = (byte) options;
        System.arraycopy(compress, 0, bytesWithOptions, 1, compress.length);
        return bytesWithOptions;
    }

    /**
     * Expand a compacted byte-array, back into a JSON string. The JSON
     * string will no longer include any extraneous whitespace from the
     * original string, and the object keys might be in a different
     * order.
     *
     * @param bytesWithOptions A compacted byte-array generated by
     *                         <a href="#compressJson">compressJson</a>
     * @return A re-constructed JSON string.
     */
    public String expandJson(byte[] bytesWithOptions) {
        int options = bytesWithOptions[0];
        byte[] bytes = new byte[bytesWithOptions.length - 1];
        System.arraycopy(bytesWithOptions, 1, bytes, 0, bytesWithOptions.length - 1);
        String expandedString;
        if ((options & ESCAPED_UPPERCASE) != 0) {
            if ((options & USE_PROTOTYPE) != 0) {
                if (prototypeCompact == null) {
                    throw new RuntimeException("Unable to expand. Do not have the prototype.");
                }
                expandedString = inflateWithPrototype(bytes);
            } else {
                expandedString = new String(expand6AndInc(bytes));
            }
            expandedString = Dictionary.lengthen(expandedString);
            expandedString = expandedString.toLowerCase();
            for (char a : "abcdefghijklmnopqrstuvwxyz".toCharArray()) {
                expandedString = expandedString.replaceAll(escapeChar + a, ("" + a).toUpperCase());
            }
        } else {
            expandedString = new String(unpack(bytes, 7));
        }
        return unwalkFormat(expandedString);
    }

    void incrementEach(byte[] bytes, int inc) {
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] += inc;
        }
    }

    byte[] expand6AndInc(byte[] sourceBytes) {
        byte[] expanded = unpack(sourceBytes, 6);
        incrementEach(expanded, 32);
        return expanded;
    }

    byte[] compress6AndDec(byte[] sourceBytes) {
        incrementEach(sourceBytes, -32);
        return pack(sourceBytes, 6);
    }

    byte[] unpack(byte[] sourceBytes, int bits) {
        byte[] resultBytes = new byte[sourceBytes.length * 8 / bits];
        int offset = 0;
        int mask = 0;
        if (bits == 6) {
            mask = 0x3f;
        } else if (bits == 7) {
            mask = 0x7f;
        } else {
            throw new RuntimeException("Can only unpack 6 or 7 bits");
        }
        int indent = 8 - bits;
        for (int i = 0; i < resultBytes.length; i++) {
            int into = offset & 0x7;
            int byteNo = offset >> 3;
            int source = 0xff & sourceBytes[byteNo];
            if (into == 0) {
                // We're starting on a byte boundary
                resultBytes[i] = (byte) (source >> indent);
            } else if (into <= indent) {
                // We're completely inside a byte boundary
                resultBytes[i] = (byte) (mask & source);
            } else {
                // We're crossing a byte boundary
                byte firstByte = (byte) (mask & (source << (into - indent)));
                byte secondByte = (byte) ((0xff & sourceBytes[byteNo + 1]) >> (16 - bits - into));
                resultBytes[i] = (byte) (firstByte | secondByte);
            }
            offset += bits;
        }
        if ((resultBytes.length > 0) && (resultBytes[resultBytes.length - 1] == 0)) {
            byte[] trimmed = new byte[resultBytes.length - 1];
            System.arraycopy(resultBytes, 0, trimmed, 0, trimmed.length);
            resultBytes = trimmed;
        }
        return resultBytes;
    }

    byte[] pack(byte[] sourceBytes, int bits) {
        int resultLength = sourceBytes.length * bits / 8;
        if (resultLength * 8 < sourceBytes.length * bits) {
            resultLength++;
        }
        byte[] resultBytes = new byte[resultLength];
        int offset = 0;
        for (int i = 0; i < resultBytes.length; i++) {
            resultBytes[i] = 0;
        }
        int indent = 8 - bits;
        for (int i = 0; i < sourceBytes.length; i++) {
            byte source = sourceBytes[i];
            int into = offset & 0x7;
            int byteNo = offset >> 3;
            if (into == 0) {
                // We're starting on a byte boundary
                resultBytes[byteNo] = (byte) (0xff & (source << indent));
            } else if (into <= indent) {
                // We're completely inside a byte boundary
                resultBytes[byteNo] |= source;
            } else {
                // We're crossing a byte boundary
                resultBytes[byteNo] |= source >> (into - indent);
                resultBytes[byteNo + 1] = (byte) (0xff & (source << (16 - bits - into)));
            }
            offset = offset + bits;
        }
        return resultBytes;
    }

    byte[] deflateWithPrototype(String sCompact) {
        byte[] output = new byte[100];
        Deflater compresser = new Deflater();
        compresser.setDictionary(prototypeCompact.getBytes());
        compresser.setInput(sCompact.getBytes());
        compresser.finish();
        int compressedDataLength = compresser.deflate(output);
        compresser.end();
        byte[] result = new byte[compressedDataLength];
        System.arraycopy(output, 0, result, 0, compressedDataLength);
        return result;
    }

    String inflateWithPrototype(byte[] deflated) {
        Inflater inflater = new Inflater();
        inflater.setInput(deflated, 0, deflated.length);
        byte[] result = new byte[400];
        int resultLength = 0;
        try {
            inflater.inflate(result);
            inflater.setDictionary(prototypeCompact.getBytes());
            resultLength = inflater.inflate(result);
            if (resultLength == result.length) {
                throw new RuntimeException("Unable to expand. Too little space");
            }
        } catch (DataFormatException dfe) {
            throw new RuntimeException("Unable to expand from prototype", dfe);
        }
        inflater.end();
        return new String(result, 0, resultLength);
    }

    private String compact(String s) {
        String json = normalizeJson(s);
        String walkFormat = walkFormat(json);
        byte[] compress;
        String tickedString = walkFormat.replaceAll("([A-Z])", ";" + "$1");
        String upperTickedString = tickedString.toUpperCase();
        return Dictionary.shorten(upperTickedString);
    }

    private String normalizeJson(String s) {
        return (new JSONObject(s)).toString();
    }

    String walkFormat(String json) {
        String s = json.trim();
        StringBuilder sb = new StringBuilder();
        char[] chars = s.toCharArray();
        boolean inString = false;
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if ((c == '>') && inString) {
                sb.append(";>");
                continue;
            }
            if ((c == '+') && inString) {
                sb.append(";+");
                continue;
            }
            if ((c == '^') && inString) {
                sb.append(";^");
                continue;
            }
            if ((c == '*') && inString) {
                sb.append(";*");
                continue;
            }
            if ((c == '<') && inString) {
                sb.append(";<");
                continue;
            }
            if ((c == ';') && inString) {
                sb.append(";;");
                continue;
            }
            if ((c == ' ') && !inString) {
                continue;
            }
            if ((c == '\n') && !inString) {
                continue;
            }
            if ((c == '}') && !inString) {
                sb.append('^');
                continue;
            }
            if ((c == ']') && !inString) {
                sb.append('^');
                continue;
            }
            if ((c == '{') && !inString) {
                sb.append('+');
                continue;
            }
            if ((c == '[') && !inString) {
                sb.append('*');
                continue;
            }
            if ((c == ',') && !inString) {
                sb.append('>');
                continue;
            }
            if ((c == ':') && !inString) {
                sb.append('>');
                continue;
            }
            sb.append(c);
        }
        String s1 = sb.toString();
        if (s1.startsWith("+")) {
            s1 = s1.substring(1);
        }
        while (s1.endsWith("^")) {
            s1 = s1.substring(0, s1.length() - 1);
        }
        return s1;
    }

    String unwalkFormat(String walk) {
        aWalk = walk;
        if (!aWalk.startsWith("*")) {
            aWalk = "+" + aWalk + "^";
        }
        pos = 0;
        Object jsonObject = null;
        try {
            jsonObject = readWalk();
        } catch (JSONException e) {
            throw new RuntimeException("Can't parse walk", e);
        }
        return jsonObject.toString();
    }

    Object readWalk() throws JSONException {
        char c = aWalk.charAt(pos);
        if (c == '+') {
            pos++;
            return readMap();
        }
        if (c == '*') {
            pos++;
            return readArray();
        }
        return readString();
    }

    JSONObject readMap() throws JSONException {
        char c = aWalk.charAt(pos);
        JSONObject result = new JSONObject();
        while ((pos < aWalk.length()) && (c != '^')) {
            String key = readString();
            c = aWalk.charAt(++pos);
            if (c == '>') {
                pos++;
            }
            Object value = readWalk();
            result.put(key, value);
            if (pos == aWalk.length() - 1) {
                break;
            }
            c = aWalk.charAt(++pos);
            if (c == '>') {
                pos++;
            }
        }
        return result;
    }

    JSONArray readArray() throws JSONException {
        char c = aWalk.charAt(pos);
        JSONArray result = new JSONArray();
        while ((pos < aWalk.length()) && (c != '^')) {
            Object value = readWalk();
            result.put(value);
            if (pos == aWalk.length() - 1) {
                break;
            }
            c = aWalk.charAt(++pos);
            if (c == '>') {
                pos++;
            }
        }
        return result;
    }

    String readString() {
        StringBuilder sb = new StringBuilder();
        char c = aWalk.charAt(pos);
        int start = pos;
        boolean escaped = false;
        while (escaped || ((c != '^') && (c != '>') && (c != '+') && (c != '*'))) {
            boolean atEscape = (c == escapeChar.charAt(0));
            if (!escaped && atEscape) {
                escaped = true;
            } else {
                escaped = false;
                sb.append(c);
            }
            if (pos == aWalk.length() - 1) {
                pos++;
                break;
            }
            c = aWalk.charAt(++pos);
        }
        pos--;
        return sb.toString();
    }
}
