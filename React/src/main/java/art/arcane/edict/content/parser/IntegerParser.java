package art.arcane.edict.content.parser;

import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

public class IntegerParser implements EdictParser<Integer> {
    @Override
    public EdictValue<Integer> parse(String s) {
        try {
            if(s.toLowerCase().endsWith("i")) {
                s = s.substring(0, s.length() - 1);
            }

            return high(Integer.parseInt(s));
        }

        catch(NumberFormatException e) {
            return low(0);
        }
    }
}
