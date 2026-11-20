package art.arcane.react.core.controller;

import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.platform.RegistryHost;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

class ComponentConfigEditorTest {
  enum Mode {
    QUIET,
    LOUD
  }

  static final class Sample extends ReactFeature {
    private int interval = 10;
    private Section section = new Section();
    private transient int runtime = 3;

    Sample() {
      super("sample");
    }
  }

  static final class Section {
    private double threshold = 0.5D;
  }

  @Test
  void coerceConvertsCommandAndWebInputToTheFieldType() {
    Assertions.assertEquals(Boolean.FALSE, ComponentConfigEditor.coerce("false", boolean.class));
    Assertions.assertNull(ComponentConfigEditor.coerce("maybe", boolean.class));
    Assertions.assertEquals(42, ComponentConfigEditor.coerce("42", int.class));
    Assertions.assertEquals(7, ComponentConfigEditor.coerce(7.9D, int.class));
    Assertions.assertEquals(2.5D, ComponentConfigEditor.coerce("2.5", double.class));
    Assertions.assertEquals(9L, ComponentConfigEditor.coerce(9, long.class));
    Assertions.assertEquals(Mode.LOUD, ComponentConfigEditor.coerce("loud", Mode.class));
    Assertions.assertNull(ComponentConfigEditor.coerce("sideways", Mode.class));
    Assertions.assertEquals(List.of("ZOMBIE", "SKELETON"), ComponentConfigEditor.coerce("ZOMBIE, SKELETON", List.class));
    Assertions.assertEquals(List.of("1", "2"), ComponentConfigEditor.coerce(List.of(1, 2), List.class));
  }

  @Test
  void readFollowsNestedSectionsAndSkipsTransientFields() {
    Sample sample = new Sample();

    Assertions.assertEquals(10, ComponentConfigEditor.read(sample, "interval"));
    Assertions.assertEquals(0.5D, ComponentConfigEditor.read(sample, "section.threshold"));
    Assertions.assertEquals(true, ComponentConfigEditor.read(sample, "enabled"));
    Assertions.assertNull(ComponentConfigEditor.read(sample, "runtime"));
    Assertions.assertNull(ComponentConfigEditor.read(sample, "missing"));
  }

  @Test
  void applyRejectsMalformedAndUnknownPathsWithoutWriting() throws Exception {
    ComponentConfigEditor.ConfigWriter writer = Mockito.mock(ComponentConfigEditor.ConfigWriter.class);
    ComponentConfigEditor editor = new ComponentConfigEditor(Mockito.mock(RegistryHost.class), writer);

    Assertions.assertFalse(editor.apply("feature", true));
    Assertions.assertFalse(editor.apply("feature.only-id", true));
    Assertions.assertFalse(editor.apply("plugin.entity-trimmer.enabled", true));
    Assertions.assertFalse(editor.apply("feature.not-registered.enabled", true));
    Mockito.verifyNoInteractions(writer);
  }
}
