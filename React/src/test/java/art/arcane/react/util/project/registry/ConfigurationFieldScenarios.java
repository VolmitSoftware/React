package art.arcane.react.util.project.registry;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.SyntheticState;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Fixture classes and copy scenarios shared by the config copy tests. The same file was compiled
 * against Curse 23.4.3 to record what the old Registered.copyConfigurationFields did; that
 * recording is the curse-23.4.3-golden.txt test resource.
 */
final class ConfigurationFieldScenarios {
  private static Class<?> syntheticHolderType;

  private ConfigurationFieldScenarios() {
  }

  @FunctionalInterface
  interface Copier {
    void copy(Object source, Object target) throws Throwable;
  }

  record Scenario(String name, Supplier<Object> source, Supplier<Object> target, boolean targetIsSource) {
    Scenario(String name, Supplier<Object> source, Supplier<Object> target) {
      this(name, source, target, false);
    }

    @Override
    public String toString() {
      return name;
    }
  }

  record Outcome(String name, boolean threw, String exception, List<String> state) {
    List<String> lines() {
      List<String> lines = new ArrayList<>();
      lines.add("scenario=" + name);
      lines.add("outcome=" + (threw ? "throws" : "ok"));
      lines.add("exception=" + exception);
      lines.addAll(state);
      return lines;
    }

    List<String> linesWithoutException() {
      List<String> lines = lines();
      lines.remove(2);
      return lines;
    }
  }

  enum Mode {
    OFF, SLOW, FAST
  }

  record Point(int x, int y) {
  }

  record RecordConfig(int amount, String label) {
  }

  static class Nested {
    int depth = 1;
    String label = "nested-default";
    List<String> tags = new ArrayList<>(List.of("a"));
  }

  static class Box<T> {
    T value;

    Box(T value) {
      this.value = value;
    }
  }

  static class Base {
    static String baseStatic = "base-static";
    private int basePrivate = 1;
    protected String baseProtected = "base-protected";
    String basePackage = "base-package";
    public long basePublic = 10L;
    private final String baseFinal;
    private transient String baseTransient = "base-transient";

    Base(String baseFinal) {
      this.baseFinal = baseFinal;
    }
  }

  static class Mid extends Base {
    private double midRatio = 0.5;
    protected List<String> midWorlds = new ArrayList<>(List.of("mid"));
    private Integer midBoxed = 700;

    Mid(String baseFinal) {
      super(baseFinal);
    }
  }

  static class Leaf extends Mid {
    static final String LEAF_CONSTANT = "leaf-constant";
    static int leafStatic = 9;
    static transient String leafStaticTransient = "leaf-static-transient";
    private final int leafFinal;
    private final List<String> leafFinalList = new ArrayList<>(List.of("final"));
    private transient int leafTransient = 12;
    private transient List<String> leafTransientList = new ArrayList<>(List.of("transient"));
    private boolean flag = false;
    private byte tiny = 1;
    private short small = 2;
    private char letter = 'a';
    private int count = 3;
    private long big = 4L;
    private float ratio = 1.5F;
    private double precise = 2.5;
    private volatile int volatileCount = 5;
    private Boolean boxedFlag = Boolean.FALSE;
    private Byte boxedTiny = (byte) 11;
    private Short boxedSmall = (short) 1200;
    private Character boxedLetter = 'b';
    private Integer boxedCount = 300;
    private Long boxedBig = 400L;
    private Float boxedRatio = 15.5F;
    private Double boxedPrecise = 25.5;
    private String text = "leaf";
    private Mode mode = Mode.OFF;
    private Point point = new Point(1, 2);
    private int[] numbers = new int[]{1, 2};
    private String[] names = new String[]{"n"};
    private int[][] grid = new int[][]{{1}, {2, 3}};
    private List<String> list = new ArrayList<>(List.of("l"));
    private Set<String> set = new LinkedHashSet<>(List.of("s"));
    private Map<String, Integer> map = new LinkedHashMap<>(Map.of("m", 1));
    private Map<String, List<Mode>> nestedGeneric = new LinkedHashMap<>(Map.of("g", List.of(Mode.OFF)));
    private Nested nested = new Nested();
    private Box<String> box = new Box<>("box");
    private Object any = "any";

    Leaf(String baseFinal, int leafFinal) {
      super(baseFinal);
      this.leafFinal = leafFinal;
    }
  }

  static class ShadowBase {
    String name = "base-name";
    int level = 1;
  }

  static class ShadowSameType extends ShadowBase {
    String name = "sub-name";
    int level = 2;
  }

  static class ShadowWideningBase {
    Object payload = "base-payload";
  }

  static class ShadowWidening extends ShadowWideningBase {
    String payload = "sub-payload";
  }

  static class ShadowIncompatibleBase {
    String before = "before";
    int limit = 1;
    String after = "after";
  }

  static class ShadowIncompatible extends ShadowIncompatibleBase {
    String own = "own";
    String limit = "sub-limit";
  }

  static class StaticShadowBase {
    String mode = "base-mode";
  }

  static class StaticShadowSub extends StaticShadowBase {
    static String mode = "static-sub-mode";
    int extra = 1;
  }

  static class SiblingA extends Mid {
    int shared = 1;
    String onlyA = "a";

    SiblingA() {
      super("sibling-a");
    }
  }

  static class SiblingB extends Mid {
    int shared = 2;
    String onlyB = "b";

    SiblingB() {
      super("sibling-b");
    }
  }

  static class SiblingTwin extends Mid {
    int shared = 3;
    String onlyA = "twin";

    SiblingTwin() {
      super("sibling-twin");
    }
  }

  static class Unrelated {
    int shared = 4;
    String onlyA = "unrelated";
  }

  static class GenericConfig<T> {
    T value;
    List<T> values = new ArrayList<>();
    Map<String, T> named = new LinkedHashMap<>();
  }

  static class StringConfig extends GenericConfig<String> {
    String extra = "extra";
  }

  static class Outer {
    int outerValue;

    Outer(int outerValue) {
      this.outerValue = outerValue;
    }

    class Inner {
      int innerValue = 1;

      int read() {
        return outerValue + innerValue;
      }
    }
  }

  static class Empty {
  }

  static Leaf defaultLeaf() {
    resetStatics();
    return new Leaf("target-final", 11);
  }

  static Leaf mutatedLeaf() {
    Leaf leaf = new Leaf("source-final", 99);
    ((Base) leaf).basePrivate = 2;
    leaf.baseProtected = "base-protected-loaded";
    leaf.basePackage = "base-package-loaded";
    leaf.basePublic = 20L;
    ((Base) leaf).baseTransient = "base-transient-loaded";
    ((Mid) leaf).midRatio = 0.75;
    leaf.midWorlds = new ArrayList<>(List.of("world", "world_nether"));
    ((Mid) leaf).midBoxed = 701;
    leaf.leafFinalList.add("loaded");
    leaf.leafTransient = 13;
    leaf.leafTransientList = new ArrayList<>(List.of("transient-loaded"));
    leaf.flag = true;
    leaf.tiny = 21;
    leaf.small = 22;
    leaf.letter = 'z';
    leaf.count = 23;
    leaf.big = 24L;
    leaf.ratio = 21.5F;
    leaf.precise = 22.5;
    leaf.volatileCount = 25;
    leaf.boxedFlag = Boolean.TRUE;
    leaf.boxedTiny = (byte) 31;
    leaf.boxedSmall = (short) 3200;
    leaf.boxedLetter = 'y';
    leaf.boxedCount = 3300;
    leaf.boxedBig = 3400L;
    leaf.boxedRatio = 35.5F;
    leaf.boxedPrecise = 36.5;
    leaf.text = "leaf-loaded";
    leaf.mode = Mode.FAST;
    leaf.point = new Point(7, 8);
    leaf.numbers = new int[]{7, 8, 9};
    leaf.names = new String[]{"x", "y"};
    leaf.grid = new int[][]{{9}};
    leaf.list = new ArrayList<>(List.of("x", "y"));
    leaf.set = new LinkedHashSet<>(List.of("x", "y"));
    leaf.map = new LinkedHashMap<>(Map.of("x", 9));
    leaf.nestedGeneric = new LinkedHashMap<>(Map.of("x", List.of(Mode.SLOW, Mode.FAST)));
    leaf.nested = new Nested();
    leaf.nested.depth = 2;
    leaf.nested.label = "nested-loaded";
    leaf.nested.tags = new ArrayList<>(List.of("x"));
    leaf.box = new Box<>("box-loaded");
    leaf.any = Integer.valueOf(4242);
    return leaf;
  }

  static Leaf leafWithNullReferences() {
    Leaf leaf = mutatedLeaf();
    leaf.baseProtected = null;
    leaf.basePackage = null;
    leaf.midWorlds = null;
    ((Mid) leaf).midBoxed = null;
    leaf.boxedFlag = null;
    leaf.boxedTiny = null;
    leaf.boxedSmall = null;
    leaf.boxedLetter = null;
    leaf.boxedCount = null;
    leaf.boxedBig = null;
    leaf.boxedRatio = null;
    leaf.boxedPrecise = null;
    leaf.text = null;
    leaf.mode = null;
    leaf.point = null;
    leaf.numbers = null;
    leaf.names = null;
    leaf.grid = null;
    leaf.list = null;
    leaf.set = null;
    leaf.map = null;
    leaf.nestedGeneric = null;
    leaf.nested = null;
    leaf.box = null;
    leaf.any = null;
    return leaf;
  }

  static void resetStatics() {
    Base.baseStatic = "base-static";
    Leaf.leafStatic = 9;
    Leaf.leafStaticTransient = "leaf-static-transient";
    StaticShadowSub.mode = "static-sub-mode";
  }

  static synchronized Class<?> syntheticHolderType() {
    if (syntheticHolderType == null) {
      syntheticHolderType = new ByteBuddy()
          .subclass(Object.class)
          .name("art.arcane.react.util.project.registry.generated.SyntheticHolder")
          .defineField("syn$value", String.class, Visibility.PUBLIC, SyntheticState.SYNTHETIC)
          .defineField("plainValue", String.class, Visibility.PUBLIC)
          .make()
          .load(ConfigurationFieldScenarios.class.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
          .getLoaded();
    }
    return syntheticHolderType;
  }

  private static Object syntheticHolder(String syntheticValue, String plainValue) {
    try {
      Class<?> type = syntheticHolderType();
      Object holder = type.getDeclaredConstructor().newInstance();
      type.getDeclaredField("syn$value").set(holder, syntheticValue);
      type.getDeclaredField("plainValue").set(holder, plainValue);
      return holder;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  static List<Scenario> all() {
    List<Scenario> scenarios = new ArrayList<>();
    scenarios.add(new Scenario("sameClassEveryFieldKind", ConfigurationFieldScenarios::mutatedLeaf, ConfigurationFieldScenarios::defaultLeaf));
    scenarios.add(new Scenario("nullSourceReferencesKeepTargetValues", ConfigurationFieldScenarios::leafWithNullReferences, ConfigurationFieldScenarios::defaultLeaf));
    scenarios.add(new Scenario("nullTargetReferencesReceiveSourceValues", ConfigurationFieldScenarios::mutatedLeaf, ConfigurationFieldScenarios::leafWithNullReferences));
    scenarios.add(new Scenario("untouchedSourceOntoMutatedTarget", ConfigurationFieldScenarios::defaultLeaf, ConfigurationFieldScenarios::mutatedLeaf));
    scenarios.add(new Scenario("selfCopy", ConfigurationFieldScenarios::mutatedLeaf, () -> null, true));
    scenarios.add(new Scenario("subclassSourceOntoSuperclassTarget", ConfigurationFieldScenarios::mutatedLeaf, () -> new Mid("target-final")));
    scenarios.add(new Scenario("superclassSourceOntoSubclassTarget", () -> {
      Mid mid = new Mid("source-final");
      mid.midWorlds = new ArrayList<>(List.of("loaded"));
      mid.basePublic = 77L;
      return mid;
    }, ConfigurationFieldScenarios::defaultLeaf));
    scenarios.add(new Scenario("siblingSourceWithDifferentFieldNames", SiblingB::new, SiblingA::new));
    scenarios.add(new Scenario("siblingSourceWithMatchingFieldNames", () -> {
      SiblingTwin twin = new SiblingTwin();
      twin.basePublic = 55L;
      twin.midWorlds = new ArrayList<>(List.of("twin-world"));
      return twin;
    }, SiblingA::new));
    scenarios.add(new Scenario("unrelatedSourceWithSomeMatchingFieldNames", Unrelated::new, SiblingA::new));
    scenarios.add(new Scenario("unrelatedSourceWithNoMatchingFieldNames", () -> "a string", ConfigurationFieldScenarios::defaultLeaf));
    scenarios.add(new Scenario("shadowedFieldSameType", () -> {
      ShadowSameType source = new ShadowSameType();
      source.name = "sub-loaded";
      source.level = 20;
      ((ShadowBase) source).name = "base-loaded";
      ((ShadowBase) source).level = 10;
      return source;
    }, ShadowSameType::new));
    scenarios.add(new Scenario("shadowedFieldWideningType", () -> {
      ShadowWidening source = new ShadowWidening();
      source.payload = "sub-loaded";
      ((ShadowWideningBase) source).payload = "base-loaded";
      return source;
    }, ShadowWidening::new));
    scenarios.add(new Scenario("shadowedFieldIncompatibleType", () -> {
      ShadowIncompatible source = new ShadowIncompatible();
      source.own = "own-loaded";
      source.limit = "sub-loaded";
      ((ShadowIncompatibleBase) source).before = "before-loaded";
      ((ShadowIncompatibleBase) source).limit = 9;
      ((ShadowIncompatibleBase) source).after = "after-loaded";
      return source;
    }, ShadowIncompatible::new));
    scenarios.add(new Scenario("staticFieldShadowsInstanceFieldSameClass", () -> {
      StaticShadowSub source = new StaticShadowSub();
      ((StaticShadowBase) source).mode = "base-loaded";
      source.extra = 2;
      return source;
    }, () -> {
      resetStatics();
      return new StaticShadowSub();
    }));
    scenarios.add(new Scenario("staticFieldShadowsInstanceFieldSubclassSource", () -> {
      StaticShadowSub source = new StaticShadowSub();
      ((StaticShadowBase) source).mode = "base-loaded";
      return source;
    }, () -> {
      resetStatics();
      return new StaticShadowBase();
    }));
    scenarios.add(new Scenario("genericFields", () -> {
      StringConfig source = new StringConfig();
      source.value = "generic-loaded";
      source.values = new ArrayList<>(List.of("v1", "v2"));
      source.named = new LinkedHashMap<>(Map.of("k", "v"));
      source.extra = "extra-loaded";
      return source;
    }, StringConfig::new));
    scenarios.add(new Scenario("genericFieldHoldingWrongRuntimeType", () -> {
      GenericConfig<Object> source = new GenericConfig<>();
      source.value = Integer.valueOf(5000);
      source.values = new ArrayList<>(List.of(1, 2));
      return source;
    }, () -> new GenericConfig<String>()));
    scenarios.add(new Scenario("recordTargetIsLeftAlone", () -> new RecordConfig(9, "loaded"), () -> new RecordConfig(1, "current")));
    scenarios.add(new Scenario("innerClassKeepsItsOwnOuterInstance", () -> {
      Outer.Inner inner = new Outer(100).new Inner();
      inner.innerValue = 5;
      return inner;
    }, () -> new Outer(200).new Inner()));
    scenarios.add(new Scenario("anonymousSubclassTargetFromBaseSource", ConfigurationFieldScenarios::mutatedLeaf, () -> {
      resetStatics();
      String captured = "captured";
      return new Leaf("target-final", 11) {
        @Override
        public String toString() {
          return captured;
        }
      };
    }));
    scenarios.add(new Scenario("syntheticNonFinalField", () -> syntheticHolder("syn-loaded", "plain-loaded"), () -> syntheticHolder("syn-current", "plain-current")));
    scenarios.add(new Scenario("jdkClassWithEncapsulatedFields", () -> new ArrayList<>(List.of("loaded")), () -> new ArrayList<>(List.of("current", "values"))));
    scenarios.add(new Scenario("emptyClass", Empty::new, Empty::new));
    scenarios.add(new Scenario("plainObjectTarget", ConfigurationFieldScenarios::mutatedLeaf, Object::new));
    scenarios.add(new Scenario("nullSourceWithFieldsOnTarget", () -> null, ConfigurationFieldScenarios::defaultLeaf));
    scenarios.add(new Scenario("nullSourceWithNoCopyableFieldsOnTarget", () -> null, () -> new RecordConfig(1, "current")));
    scenarios.add(new Scenario("nullTarget", ConfigurationFieldScenarios::mutatedLeaf, () -> null));
    return scenarios;
  }

  static Scenario byName(String name) {
    for (Scenario scenario : all()) {
      if (scenario.name().equals(name)) {
        return scenario;
      }
    }
    throw new IllegalArgumentException("Unknown scenario " + name);
  }

  static Outcome run(Scenario scenario, Copier copier) {
    Object source = scenario.source().get();
    Object target = scenario.targetIsSource() ? source : scenario.target().get();
    Map<Field, Object> before = snapshot(target);
    boolean threw = false;
    String exception = "none";
    try {
      copier.copy(source, target);
    } catch (Throwable e) {
      threw = true;
      exception = e.getClass().getName() + (e.getCause() == null ? "" : " <- " + e.getCause().getClass().getName());
    }
    return new Outcome(scenario.name(), threw, exception, describe(source, target, before));
  }

  static List<Field> hierarchyFields(Class<?> type) {
    List<Field> fields = new ArrayList<>();
    for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        try {
          field.setAccessible(true);
        } catch (RuntimeException inaccessible) {
          continue;
        }
        fields.add(field);
      }
    }
    return fields;
  }

  static boolean copyable(Field field) {
    int modifiers = field.getModifiers();
    return !Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers) && !Modifier.isTransient(modifiers);
  }

  static Field findByName(Class<?> type, String name) {
    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      try {
        Field field = current.getDeclaredField(name);
        field.setAccessible(true);
        return field;
      } catch (NoSuchFieldException ignored) {
      }
    }
    return null;
  }

  static Object read(Field field, Object instance) {
    try {
      return field.get(Modifier.isStatic(field.getModifiers()) ? null : instance);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Map<Field, Object> snapshot(Object target) {
    Map<Field, Object> values = new LinkedHashMap<>();
    if (target == null) {
      return values;
    }
    for (Field field : hierarchyFields(target.getClass())) {
      values.put(field, read(field, target));
    }
    return values;
  }

  private static List<String> describe(Object source, Object target, Map<Field, Object> before) {
    List<String> lines = new ArrayList<>();
    if (target == null) {
      lines.add("target=null");
      return lines;
    }
    for (Field field : hierarchyFields(target.getClass())) {
      Object value = read(field, target);
      StringBuilder line = new StringBuilder();
      line.append(field.getDeclaringClass().getSimpleName()).append('.').append(field.getName())
          .append(" = ").append(render(value, new IdentityHashMap<>()));
      if (matches(field, value, before.get(field))) {
        line.append(" [kept]");
      }
      if (source != null && source != target) {
        if (field.getDeclaringClass().isInstance(source) && matches(field, value, read(field, source))) {
          line.append(" [source]");
        }
        Field named = findByName(source.getClass(), field.getName());
        if (named != null && !named.equals(field) && named.getType().isPrimitive() == field.getType().isPrimitive()
            && matches(field, value, read(named, source))) {
          line.append(" [sourceByName:").append(named.getDeclaringClass().getSimpleName()).append(']');
        }
      }
      lines.add(line.toString());
    }
    return lines;
  }

  private static boolean matches(Field field, Object value, Object other) {
    if (field.getType().isPrimitive()) {
      return value.equals(other);
    }
    return value == other;
  }

  static String render(Object value, IdentityHashMap<Object, Boolean> seen) {
    if (value == null) {
      return "null";
    }
    Class<?> type = value.getClass();
    if (type.isArray()) {
      StringBuilder out = new StringBuilder("[");
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        if (i > 0) {
          out.append(", ");
        }
        out.append(render(Array.get(value, i), seen));
      }
      return out.append(']').toString();
    }
    if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Character
        || value instanceof Enum<?> || value instanceof Record) {
      return type.getSimpleName() + "(" + value + ")";
    }
    if (value instanceof Collection<?> || value instanceof Map<?, ?>) {
      return (value instanceof Map<?, ?> ? "Map" : value instanceof Set<?> ? "Set" : "List") + "(" + value + ")";
    }
    if (seen.put(value, Boolean.TRUE) != null) {
      return type.getSimpleName() + "{...}";
    }
    StringBuilder out = new StringBuilder(type.getSimpleName()).append('{');
    boolean first = true;
    for (Field field : hierarchyFields(type)) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      if (!first) {
        out.append(", ");
      }
      first = false;
      out.append(field.getName()).append('=').append(render(read(field, value), seen));
    }
    return out.append('}').toString();
  }
}
