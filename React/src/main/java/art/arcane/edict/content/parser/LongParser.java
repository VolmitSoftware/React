package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

public class LongParser implements EdictParser<Long> {
    @Override
    public EdictValue<Long> parse(String s) {
        try {
            if(s.toLowerCase().endsWith("l")) {
                s = s.substring(0, s.length() - 1);
            }

            return high(Long.parseLong(s));
        }

        catch(NumberFormatException e) {
            return low(0L);
        }
    }
}
