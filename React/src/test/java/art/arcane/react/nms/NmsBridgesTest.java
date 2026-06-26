package art.arcane.react.nms;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class NmsBridgesTest {

    @ParameterizedTest
    @CsvSource({
            "'git-Purpur-2398 (MC: 26.2)', '26.2'",
            "'git-Paper-196 (MC: 1.21.11)', '1.21.11'",
            "'26.2-R0.1-SNAPSHOT', '26.2'",
            "'1.21.11-R0.1-SNAPSHOT', '1.21.11'",
            "'26.2', '26.2'",
            "'26.2.0', '26.2'",
            "'26.2.7', '26.2'",
            "'1.21', '1.21'",
            "'1.20.4', '1.20.4'"
    })
    public void extractMcVersion_parsesDecoratedAndPatchForms(String raw, String expected) {
        Assertions.assertEquals(expected, NmsBridges.extractMcVersion(raw));
    }

    @Test
    public void extractMcVersion_noVersionPresentReturnsEmpty() {
        Assertions.assertEquals("", NmsBridges.extractMcVersion("no-version-here"));
        Assertions.assertEquals("", NmsBridges.extractMcVersion("Purpur"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"26.2", "1.21.11"})
    public void tagFor_supportedVersionsMapToR1Tag(String mcVersion) {
        Assertions.assertEquals("v26_2_R1", NmsBridges.tagFor(mcVersion));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.21", "1.20.4", "26.1", "26.3", "27.0"})
    public void tagFor_unsupportedVersionsReturnEmpty(String mcVersion) {
        Assertions.assertEquals("", NmsBridges.tagFor(mcVersion));
    }

    @Test
    public void tagFor_nullOrEmptyReturnsEmpty() {
        Assertions.assertEquals("", NmsBridges.tagFor(null));
        Assertions.assertEquals("", NmsBridges.tagFor(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "git-Purpur-2398 (MC: 26.2)",
            "git-Paper-196 (MC: 1.21.11)",
            "26.2-R0.1-SNAPSHOT",
            "1.21.11-R0.1-SNAPSHOT",
            "26.2.0",
            "26.2.7"
    })
    public void decoratedAndPatchZeroVersionsMapToR1Tag(String raw) {
        Assertions.assertEquals("v26_2_R1", NmsBridges.tagFor(NmsBridges.extractMcVersion(raw)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1.21-R0.1-SNAPSHOT",
            "git-Paper-130 (MC: 1.20.4)",
            "no-version-here"
    })
    public void unsupportedDecoratedVersionsMapToEmptyTag(String raw) {
        Assertions.assertEquals("", NmsBridges.tagFor(NmsBridges.extractMcVersion(raw)));
    }
}
