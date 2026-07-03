package art.arcane.react.core.gui;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

class PresetHeadlessApplyTest {

    @Test
    void applyPresetHeadlessIsPublicStatic() throws NoSuchMethodException {
        Method method = ReactConfigGUI.class.getMethod("applyPresetHeadless", String.class);
        Assertions.assertTrue(Modifier.isPublic(method.getModifiers()));
        Assertions.assertTrue(Modifier.isStatic(method.getModifiers()));
        Assertions.assertEquals(int.class, method.getReturnType());
    }

    @Test
    void nullPresetReturnsNegativeOne() throws Exception {
        Method method = ReactConfigGUI.class.getMethod("applyPresetHeadless", String.class);
        int result = (int) method.invoke(null, (Object) null);
        Assertions.assertEquals(-1, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bogus", "BOGUS", "medium", "", "  ", "unknown", "null"})
    void unknownPresetReturnsNegativeOne(String preset) throws Exception {
        Method method = ReactConfigGUI.class.getMethod("applyPresetHeadless", String.class);
        int result = (int) method.invoke(null, preset);
        Assertions.assertEquals(-1, result,
            "Expected -1 sentinel for unknown preset: '" + preset + "'");
    }
}
