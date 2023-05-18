package art.arcane.edict.content.parser;

import art.arcane.edict.api.Confidence;
import art.arcane.edict.api.parser.EdictParser;
import art.arcane.edict.api.parser.EdictValue;

public class IntegerHexParser implements EdictParser<Integer> {
    @Override
    public EdictValue<Integer> parse(String s) {
        Confidence c = Confidence.LOW;
        if(s.toLowerCase().endsWith("i")) {
            s = s.substring(0, s.length() - 1);
        }

        if(s.toLowerCase().startsWith("0x")) {
            s = s.substring(2);
            c = Confidence.HIGH;
        }

        if(s.startsWith("#")) {
            c = Confidence.HIGH;
        }

        try {
            return of(Integer.parseInt(s, 16), c);
        }

        catch(NumberFormatException e) {
            return low(0);
        }
    }
}
