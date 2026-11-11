package art.arcane.react.util.project.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What Registered.copyConfigurationFields did at 3b799037, recorded by running that method body
 * against the real Curse 23.4.3 jar on JDK 25 with the scenarios in ConfigurationFieldScenarios.
 * Regenerate it the same way if the scenarios change; do not edit the text by hand.
 */
final class CurseRecordedBehavior {
  private static final String RECORDING = """
      scenario=sameClassEveryFieldKind
      outcome=ok
      exception=none
      Leaf.LEAF_CONSTANT = String(leaf-constant) [kept] [source]
      Leaf.leafStatic = Integer(9) [kept] [source]
      Leaf.leafStaticTransient = String(leaf-static-transient) [kept] [source]
      Leaf.leafFinal = Integer(11) [kept]
      Leaf.leafFinalList = List([final]) [kept]
      Leaf.leafTransient = Integer(12) [kept]
      Leaf.leafTransientList = List([transient]) [kept]
      Leaf.flag = Boolean(true) [source]
      Leaf.tiny = Byte(21) [source]
      Leaf.small = Short(22) [source]
      Leaf.letter = Character(z) [source]
      Leaf.count = Integer(23) [source]
      Leaf.big = Long(24) [source]
      Leaf.ratio = Float(21.5) [source]
      Leaf.precise = Double(22.5) [source]
      Leaf.volatileCount = Integer(25) [source]
      Leaf.boxedFlag = Boolean(true) [source]
      Leaf.boxedTiny = Byte(31) [source]
      Leaf.boxedSmall = Short(3200) [source]
      Leaf.boxedLetter = Character(y) [source]
      Leaf.boxedCount = Integer(3300) [source]
      Leaf.boxedBig = Long(3400) [source]
      Leaf.boxedRatio = Float(35.5) [source]
      Leaf.boxedPrecise = Double(36.5) [source]
      Leaf.text = String(leaf-loaded) [source]
      Leaf.mode = Mode(FAST) [source]
      Leaf.point = Point(Point[x=7, y=8]) [source]
      Leaf.numbers = [Integer(7), Integer(8), Integer(9)] [source]
      Leaf.names = [String(x), String(y)] [source]
      Leaf.grid = [[Integer(9)]] [source]
      Leaf.list = List([x, y]) [source]
      Leaf.set = Set([x, y]) [source]
      Leaf.map = Map({x=9}) [source]
      Leaf.nestedGeneric = Map({x=[SLOW, FAST]}) [source]
      Leaf.nested = Nested{depth=Integer(2), label=String(nested-loaded), tags=List([x])} [source]
      Leaf.box = Box{value=String(box-loaded)} [source]
      Leaf.any = Integer(4242) [source]
      Mid.midRatio = Double(0.75) [source]
      Mid.midWorlds = List([world, world_nether]) [source]
      Mid.midBoxed = Integer(701) [source]
      Base.baseStatic = String(base-static) [kept] [source]
      Base.basePrivate = Integer(2) [source]
      Base.baseProtected = String(base-protected-loaded) [source]
      Base.basePackage = String(base-package-loaded) [source]
      Base.basePublic = Long(20) [source]
      Base.baseFinal = String(target-final) [kept]
      Base.baseTransient = String(base-transient) [kept]

      scenario=nullSourceReferencesKeepTargetValues
      outcome=ok
      exception=none
      Leaf.LEAF_CONSTANT = String(leaf-constant) [kept] [source]
      Leaf.leafStatic = Integer(9) [kept] [source]
      Leaf.leafStaticTransient = String(leaf-static-transient) [kept] [source]
      Leaf.leafFinal = Integer(11) [kept]
      Leaf.leafFinalList = List([final]) [kept]
      Leaf.leafTransient = Integer(12) [kept]
      Leaf.leafTransientList = List([transient]) [kept]
      Leaf.flag = Boolean(true) [source]
      Leaf.tiny = Byte(21) [source]
      Leaf.small = Short(22) [source]
      Leaf.letter = Character(z) [source]
      Leaf.count = Integer(23) [source]
      Leaf.big = Long(24) [source]
      Leaf.ratio = Float(21.5) [source]
      Leaf.precise = Double(22.5) [source]
      Leaf.volatileCount = Integer(25) [source]
      Leaf.boxedFlag = Boolean(false) [kept]
      Leaf.boxedTiny = Byte(11) [kept]
      Leaf.boxedSmall = Short(1200) [kept]
      Leaf.boxedLetter = Character(b) [kept]
      Leaf.boxedCount = Integer(300) [kept]
      Leaf.boxedBig = Long(400) [kept]
      Leaf.boxedRatio = Float(15.5) [kept]
      Leaf.boxedPrecise = Double(25.5) [kept]
      Leaf.text = String(leaf) [kept]
      Leaf.mode = Mode(OFF) [kept]
      Leaf.point = Point(Point[x=1, y=2]) [kept]
      Leaf.numbers = [Integer(1), Integer(2)] [kept]
      Leaf.names = [String(n)] [kept]
      Leaf.grid = [[Integer(1)], [Integer(2), Integer(3)]] [kept]
      Leaf.list = List([l]) [kept]
      Leaf.set = Set([s]) [kept]
      Leaf.map = Map({m=1}) [kept]
      Leaf.nestedGeneric = Map({g=[OFF]}) [kept]
      Leaf.nested = Nested{depth=Integer(1), label=String(nested-default), tags=List([a])} [kept]
      Leaf.box = Box{value=String(box)} [kept]
      Leaf.any = String(any) [kept]
      Mid.midRatio = Double(0.75) [source]
      Mid.midWorlds = List([mid]) [kept]
      Mid.midBoxed = Integer(700) [kept]
      Base.baseStatic = String(base-static) [kept] [source]
      Base.basePrivate = Integer(2) [source]
      Base.baseProtected = String(base-protected) [kept]
      Base.basePackage = String(base-package) [kept]
      Base.basePublic = Long(20) [source]
      Base.baseFinal = String(target-final) [kept]
      Base.baseTransient = String(base-transient) [kept]

      scenario=nullTargetReferencesReceiveSourceValues
      outcome=ok
      exception=none
      Leaf.LEAF_CONSTANT = String(leaf-constant) [kept] [source]
      Leaf.leafStatic = Integer(9) [kept] [source]
      Leaf.leafStaticTransient = String(leaf-static-transient) [kept] [source]
      Leaf.leafFinal = Integer(99) [kept] [source]
      Leaf.leafFinalList = List([final, loaded]) [kept]
      Leaf.leafTransient = Integer(13) [kept] [source]
      Leaf.leafTransientList = List([transient-loaded]) [kept]
      Leaf.flag = Boolean(true) [kept] [source]
      Leaf.tiny = Byte(21) [kept] [source]
      Leaf.small = Short(22) [kept] [source]
      Leaf.letter = Character(z) [kept] [source]
      Leaf.count = Integer(23) [kept] [source]
      Leaf.big = Long(24) [kept] [source]
      Leaf.ratio = Float(21.5) [kept] [source]
      Leaf.precise = Double(22.5) [kept] [source]
      Leaf.volatileCount = Integer(25) [kept] [source]
      Leaf.boxedFlag = Boolean(true) [source]
      Leaf.boxedTiny = Byte(31) [source]
      Leaf.boxedSmall = Short(3200) [source]
      Leaf.boxedLetter = Character(y) [source]
      Leaf.boxedCount = Integer(3300) [source]
      Leaf.boxedBig = Long(3400) [source]
      Leaf.boxedRatio = Float(35.5) [source]
      Leaf.boxedPrecise = Double(36.5) [source]
      Leaf.text = String(leaf-loaded) [source]
      Leaf.mode = Mode(FAST) [source]
      Leaf.point = Point(Point[x=7, y=8]) [source]
      Leaf.numbers = [Integer(7), Integer(8), Integer(9)] [source]
      Leaf.names = [String(x), String(y)] [source]
      Leaf.grid = [[Integer(9)]] [source]
      Leaf.list = List([x, y]) [source]
      Leaf.set = Set([x, y]) [source]
      Leaf.map = Map({x=9}) [source]
      Leaf.nestedGeneric = Map({x=[SLOW, FAST]}) [source]
      Leaf.nested = Nested{depth=Integer(2), label=String(nested-loaded), tags=List([x])} [source]
      Leaf.box = Box{value=String(box-loaded)} [source]
      Leaf.any = Integer(4242) [source]
      Mid.midRatio = Double(0.75) [kept] [source]
      Mid.midWorlds = List([world, world_nether]) [source]
      Mid.midBoxed = Integer(701) [source]
      Base.baseStatic = String(base-static) [kept] [source]
      Base.basePrivate = Integer(2) [kept] [source]
      Base.baseProtected = String(base-protected-loaded) [source]
      Base.basePackage = String(base-package-loaded) [source]
      Base.basePublic = Long(20) [kept] [source]
      Base.baseFinal = String(source-final) [kept] [source]
      Base.baseTransient = String(base-transient-loaded) [kept] [source]

      scenario=untouchedSourceOntoMutatedTarget
      outcome=ok
      exception=none
      Leaf.LEAF_CONSTANT = String(leaf-constant) [kept] [source]
      Leaf.leafStatic = Integer(9) [kept] [source]
      Leaf.leafStaticTransient = String(leaf-static-transient) [kept] [source]
      Leaf.leafFinal = Integer(99) [kept]
      Leaf.leafFinalList = List([final, loaded]) [kept]
      Leaf.leafTransient = Integer(13) [kept]
      Leaf.leafTransientList = List([transient-loaded]) [kept]
      Leaf.flag = Boolean(false) [source]
      Leaf.tiny = Byte(1) [source]
      Leaf.small = Short(2) [source]
      Leaf.letter = Character(a) [source]
      Leaf.count = Integer(3) [source]
      Leaf.big = Long(4) [source]
      Leaf.ratio = Float(1.5) [source]
      Leaf.precise = Double(2.5) [source]
      Leaf.volatileCount = Integer(5) [source]
      Leaf.boxedFlag = Boolean(false) [source]
      Leaf.boxedTiny = Byte(11) [source]
      Leaf.boxedSmall = Short(1200) [source]
      Leaf.boxedLetter = Character(b) [source]
      Leaf.boxedCount = Integer(300) [source]
      Leaf.boxedBig = Long(400) [source]
      Leaf.boxedRatio = Float(15.5) [source]
      Leaf.boxedPrecise = Double(25.5) [source]
      Leaf.text = String(leaf) [source]
      Leaf.mode = Mode(OFF) [source]
      Leaf.point = Point(Point[x=1, y=2]) [source]
      Leaf.numbers = [Integer(1), Integer(2)] [source]
      Leaf.names = [String(n)] [source]
      Leaf.grid = [[Integer(1)], [Integer(2), Integer(3)]] [source]
      Leaf.list = List([l]) [source]
      Leaf.set = Set([s]) [source]
      Leaf.map = Map({m=1}) [source]
      Leaf.nestedGeneric = Map({g=[OFF]}) [source]
      Leaf.nested = Nested{depth=Integer(1), label=String(nested-default), tags=List([a])} [source]
      Leaf.box = Box{value=String(box)} [source]
      Leaf.any = String(any) [source]
      Mid.midRatio = Double(0.5) [source]
      Mid.midWorlds = List([mid]) [source]
      Mid.midBoxed = Integer(700) [source]
      Base.baseStatic = String(base-static) [kept] [source]
      Base.basePrivate = Integer(1) [source]
      Base.baseProtected = String(base-protected) [source]
      Base.basePackage = String(base-package) [source]
      Base.basePublic = Long(10) [source]
      Base.baseFinal = String(source-final) [kept]
      Base.baseTransient = String(base-transient-loaded) [kept]

      scenario=selfCopy
      outcome=ok
      exception=none
      Leaf.LEAF_CONSTANT = String(leaf-constant) [kept]
      Leaf.leafStatic = Integer(9) [kept]
      Leaf.leafStaticTransient = String(leaf-static-transient) [kept]
      Leaf.leafFinal = Integer(99) [kept]
      Leaf.leafFinalList = List([final, loaded]) [kept]
      Leaf.leafTransient = Integer(13) [kept]
      Leaf.leafTransientList = List([transient-loaded]) [kept]
      Leaf.flag = Boolean(true) [kept]
      Leaf.tiny = Byte(21) [kept]
      Leaf.small = Short(22) [kept]
      Leaf.letter = Character(z) [kept]
      Leaf.count = Integer(23) [kept]
      Leaf.big = Long(24) [kept]
      Leaf.ratio = Float(21.5) [kept]
      Leaf.precise = Double(22.5) [kept]
      Leaf.volatileCount = Integer(25) [kept]
      Leaf.boxedFlag = Boolean(true) [kept]
      Leaf.boxedTiny = Byte(31) [kept]
      Leaf.boxedSmall = Short(3200) [kept]
      Leaf.boxedLetter = Character(y) [kept]
      Leaf.boxedCount = Integer(3300) [kept]
      Leaf.boxedBig = Long(3400) [kept]
      Leaf.boxedRatio = Float(35.5) [kept]
      Leaf.boxedPrecise = Double(36.5) [kept]
      Leaf.text = String(leaf-loaded) [kept]
      Leaf.mode = Mode(FAST) [kept]
      Leaf.point = Point(Point[x=7, y=8]) [kept]
      Leaf.numbers = [Integer(7), Integer(8), Integer(9)] [kept]
      Leaf.names = [String(x), String(y)] [kept]
      Leaf.grid = [[Integer(9)]] [kept]
      Leaf.list = List([x, y]) [kept]
      Leaf.set = Set([x, y]) [kept]
      Leaf.map = Map({x=9}) [kept]
      Leaf.nestedGeneric = Map({x=[SLOW, FAST]}) [kept]
      Leaf.nested = Nested{depth=Integer(2), label=String(nested-loaded), tags=List([x])} [kept]
      Leaf.box = Box{value=String(box-loaded)} [kept]
      Leaf.any = Integer(4242) [kept]
      Mid.midRatio = Double(0.75) [kept]
      Mid.midWorlds = List([world, world_nether]) [kept]
      Mid.midBoxed = Integer(701) [kept]
      Base.baseStatic = String(base-static) [kept]
      Base.basePrivate = Integer(2) [kept]
      Base.baseProtected = String(base-protected-loaded) [kept]
      Base.basePackage = String(base-package-loaded) [kept]
      Base.basePublic = Long(20) [kept]
      Base.baseFinal = String(source-final) [kept]
      Base.baseTransient = String(base-transient-loaded) [kept]

      scenario=subclassSourceOntoSuperclassTarget
      outcome=ok
      exception=none
      Mid.midRatio = Double(0.75) [source]
      Mid.midWorlds = List([world, world_nether]) [source]
      Mid.midBoxed = Integer(701) [source]
      Base.baseStatic = String(base-static) [kept] [source]
      Base.basePrivate = Integer(2) [source]
      Base.baseProtected = String(base-protected-loaded) [source]
      Base.basePackage = String(base-package-loaded) [source]
      Base.basePublic = Long(20) [source]
      Base.baseFinal = String(target-final) [kept]
      Base.baseTransient = String(base-transient) [kept]

      scenario=superclassSourceOntoSubclassTarget
      outcome=throws
      exception=java.util.NoSuchElementException
      Leaf.LEAF_CONSTANT = String(leaf-constant) [kept]
      Leaf.leafStatic = Integer(9) [kept]
      Leaf.leafStaticTransient = String(leaf-static-transient) [kept]
      Leaf.leafFinal = Integer(11) [kept]
      Leaf.leafFinalList = List([final]) [kept]
      Leaf.leafTransient = Integer(12) [kept]
      Leaf.leafTransientList = List([transient]) [kept]
      Leaf.flag = Boolean(false) [kept]
      Leaf.tiny = Byte(1) [kept]
      Leaf.small = Short(2) [kept]
      Leaf.letter = Character(a) [kept]
      Leaf.count = Integer(3) [kept]
      Leaf.big = Long(4) [kept]
      Leaf.ratio = Float(1.5) [kept]
      Leaf.precise = Double(2.5) [kept]
      Leaf.volatileCount = Integer(5) [kept]
      Leaf.boxedFlag = Boolean(false) [kept]
      Leaf.boxedTiny = Byte(11) [kept]
      Leaf.boxedSmall = Short(1200) [kept]
      Leaf.boxedLetter = Character(b) [kept]
      Leaf.boxedCount = Integer(300) [kept]
      Leaf.boxedBig = Long(400) [kept]
      Leaf.boxedRatio = Float(15.5) [kept]
      Leaf.boxedPrecise = Double(25.5) [kept]
      Leaf.text = String(leaf) [kept]
      Leaf.mode = Mode(OFF) [kept]
      Leaf.point = Point(Point[x=1, y=2]) [kept]
      Leaf.numbers = [Integer(1), Integer(2)] [kept]
      Leaf.names = [String(n)] [kept]
      Leaf.grid = [[Integer(1)], [Integer(2), Integer(3)]] [kept]
      Leaf.list = List([l]) [kept]
      Leaf.set = Set([s]) [kept]
      Leaf.map = Map({m=1}) [kept]
      Leaf.nestedGeneric = Map({g=[OFF]}) [kept]
      Leaf.nested = Nested{depth=Integer(1), label=String(nested-default), tags=List([a])} [kept]
      Leaf.box = Box{value=String(box)} [kept]
      Leaf.any = String(any) [kept]
      Mid.midRatio = Double(0.5) [kept] [source]
      Mid.midWorlds = List([mid]) [kept]
      Mid.midBoxed = Integer(700) [kept]
      Base.baseStatic = String(base-static) [kept] [source]
      Base.basePrivate = Integer(1) [kept] [source]
      Base.baseProtected = String(base-protected) [kept] [source]
      Base.basePackage = String(base-package) [kept] [source]
      Base.basePublic = Long(10) [kept]
      Base.baseFinal = String(target-final) [kept]
      Base.baseTransient = String(base-transient) [kept] [source]

      scenario=siblingSourceWithDifferentFieldNames
      outcome=throws
      exception=java.util.NoSuchElementException
      SiblingA.shared = Integer(2) [sourceByName:SiblingB]
      SiblingA.onlyA = String(a) [kept]
      Mid.midRatio = Double(0.5) [kept] [source]
      Mid.midWorlds = List([mid]) [kept]
      Mid.midBoxed = Integer(700) [kept]
      Base.baseStatic = String(base-static) [kept] [source]
      Base.basePrivate = Integer(1) [kept] [source]
      Base.baseProtected = String(base-protected) [kept] [source]
      Base.basePackage = String(base-package) [kept] [source]
      Base.basePublic = Long(10) [kept] [source]
      Base.baseFinal = String(sibling-a) [kept]
      Base.baseTransient = String(base-transient) [kept] [source]

      scenario=siblingSourceWithMatchingFieldNames
      outcome=ok
      exception=none
      SiblingA.shared = Integer(3) [sourceByName:SiblingTwin]
      SiblingA.onlyA = String(twin) [sourceByName:SiblingTwin]
      Mid.midRatio = Double(0.5) [kept] [source]
      Mid.midWorlds = List([twin-world]) [source]
      Mid.midBoxed = Integer(700) [source]
      Base.baseStatic = String(base-static) [kept] [source]
      Base.basePrivate = Integer(1) [kept] [source]
      Base.baseProtected = String(base-protected) [kept] [source]
      Base.basePackage = String(base-package) [kept] [source]
      Base.basePublic = Long(55) [source]
      Base.baseFinal = String(sibling-a) [kept]
      Base.baseTransient = String(base-transient) [kept] [source]

      scenario=unrelatedSourceWithSomeMatchingFieldNames
      outcome=throws
      exception=java.util.NoSuchElementException
      SiblingA.shared = Integer(4) [sourceByName:Unrelated]
      SiblingA.onlyA = String(unrelated) [sourceByName:Unrelated]
      Mid.midRatio = Double(0.5) [kept]
      Mid.midWorlds = List([mid]) [kept]
      Mid.midBoxed = Integer(700) [kept]
      Base.baseStatic = String(base-static) [kept]
      Base.basePrivate = Integer(1) [kept]
      Base.baseProtected = String(base-protected) [kept]
      Base.basePackage = String(base-package) [kept]
      Base.basePublic = Long(10) [kept]
      Base.baseFinal = String(sibling-a) [kept]
      Base.baseTransient = String(base-transient) [kept]

      scenario=unrelatedSourceWithNoMatchingFieldNames
      outcome=throws
      exception=java.util.NoSuchElementException
      Leaf.LEAF_CONSTANT = String(leaf-constant) [kept]
      Leaf.leafStatic = Integer(9) [kept]
      Leaf.leafStaticTransient = String(leaf-static-transient) [kept]
      Leaf.leafFinal = Integer(11) [kept]
      Leaf.leafFinalList = List([final]) [kept]
      Leaf.leafTransient = Integer(12) [kept]
      Leaf.leafTransientList = List([transient]) [kept]
      Leaf.flag = Boolean(false) [kept]
      Leaf.tiny = Byte(1) [kept]
      Leaf.small = Short(2) [kept]
      Leaf.letter = Character(a) [kept]
      Leaf.count = Integer(3) [kept]
      Leaf.big = Long(4) [kept]
      Leaf.ratio = Float(1.5) [kept]
      Leaf.precise = Double(2.5) [kept]
      Leaf.volatileCount = Integer(5) [kept]
      Leaf.boxedFlag = Boolean(false) [kept]
      Leaf.boxedTiny = Byte(11) [kept]
      Leaf.boxedSmall = Short(1200) [kept]
      Leaf.boxedLetter = Character(b) [kept]
      Leaf.boxedCount = Integer(300) [kept]
      Leaf.boxedBig = Long(400) [kept]
      Leaf.boxedRatio = Float(15.5) [kept]
      Leaf.boxedPrecise = Double(25.5) [kept]
      Leaf.text = String(leaf) [kept]
      Leaf.mode = Mode(OFF) [kept]
      Leaf.point = Point(Point[x=1, y=2]) [kept]
      Leaf.numbers = [Integer(1), Integer(2)] [kept]
      Leaf.names = [String(n)] [kept]
      Leaf.grid = [[Integer(1)], [Integer(2), Integer(3)]] [kept]
      Leaf.list = List([l]) [kept]
      Leaf.set = Set([s]) [kept]
      Leaf.map = Map({m=1}) [kept]
      Leaf.nestedGeneric = Map({g=[OFF]}) [kept]
      Leaf.nested = Nested{depth=Integer(1), label=String(nested-default), tags=List([a])} [kept]
      Leaf.box = Box{value=String(box)} [kept]
      Leaf.any = String(any) [kept]
      Mid.midRatio = Double(0.5) [kept]
      Mid.midWorlds = List([mid]) [kept]
      Mid.midBoxed = Integer(700) [kept]
      Base.baseStatic = String(base-static) [kept]
      Base.basePrivate = Integer(1) [kept]
      Base.baseProtected = String(base-protected) [kept]
      Base.basePackage = String(base-package) [kept]
      Base.basePublic = Long(10) [kept]
      Base.baseFinal = String(target-final) [kept]
      Base.baseTransient = String(base-transient) [kept]

      scenario=shadowedFieldSameType
      outcome=ok
      exception=none
      ShadowSameType.name = String(sub-loaded) [source]
      ShadowSameType.level = Integer(20) [source]
      ShadowBase.name = String(sub-loaded) [sourceByName:ShadowSameType]
      ShadowBase.level = Integer(20) [sourceByName:ShadowSameType]

      scenario=shadowedFieldWideningType
      outcome=ok
      exception=none
      ShadowWidening.payload = String(sub-loaded) [source]
      ShadowWideningBase.payload = String(sub-loaded) [sourceByName:ShadowWidening]

      scenario=shadowedFieldIncompatibleType
      outcome=throws
      exception=java.lang.RuntimeException <- java.lang.IllegalArgumentException
      ShadowIncompatible.own = String(own-loaded) [source]
      ShadowIncompatible.limit = String(sub-loaded) [source]
      ShadowIncompatibleBase.before = String(before-loaded) [source]
      ShadowIncompatibleBase.limit = Integer(1) [kept]
      ShadowIncompatibleBase.after = String(after) [kept]

      scenario=staticFieldShadowsInstanceFieldSameClass
      outcome=ok
      exception=none
      StaticShadowSub.mode = String(static-sub-mode) [kept] [source]
      StaticShadowSub.extra = Integer(2) [source]
      StaticShadowBase.mode = String(static-sub-mode) [sourceByName:StaticShadowSub]

      scenario=staticFieldShadowsInstanceFieldSubclassSource
      outcome=ok
      exception=none
      StaticShadowBase.mode = String(static-sub-mode) [sourceByName:StaticShadowSub]

      scenario=genericFields
      outcome=ok
      exception=none
      StringConfig.extra = String(extra-loaded) [source]
      GenericConfig.value = String(generic-loaded) [source]
      GenericConfig.values = List([v1, v2]) [source]
      GenericConfig.named = Map({k=v}) [source]

      scenario=genericFieldHoldingWrongRuntimeType
      outcome=ok
      exception=none
      GenericConfig.value = Integer(5000) [source]
      GenericConfig.values = List([1, 2]) [source]
      GenericConfig.named = Map({}) [source]

      scenario=recordTargetIsLeftAlone
      outcome=ok
      exception=none
      RecordConfig.amount = Integer(1) [kept]
      RecordConfig.label = String(current) [kept]

      scenario=innerClassKeepsItsOwnOuterInstance
      outcome=ok
      exception=none
      Inner.innerValue = Integer(5) [source]
      Inner.this$0 = Outer{outerValue=Integer(200)} [kept]

      scenario=anonymousSubclassTargetFromBaseSource
      outcome=ok
      exception=none
      .val$captured = String(captured) [kept]
      Leaf.LEAF_CONSTANT = String(leaf-constant) [kept] [source]
      Leaf.leafStatic = Integer(9) [kept] [source]
      Leaf.leafStaticTransient = String(leaf-static-transient) [kept] [source]
      Leaf.leafFinal = Integer(11) [kept]
      Leaf.leafFinalList = List([final]) [kept]
      Leaf.leafTransient = Integer(12) [kept]
      Leaf.leafTransientList = List([transient]) [kept]
      Leaf.flag = Boolean(true) [source]
      Leaf.tiny = Byte(21) [source]
      Leaf.small = Short(22) [source]
      Leaf.letter = Character(z) [source]
      Leaf.count = Integer(23) [source]
      Leaf.big = Long(24) [source]
      Leaf.ratio = Float(21.5) [source]
      Leaf.precise = Double(22.5) [source]
      Leaf.volatileCount = Integer(25) [source]
      Leaf.boxedFlag = Boolean(true) [source]
      Leaf.boxedTiny = Byte(31) [source]
      Leaf.boxedSmall = Short(3200) [source]
      Leaf.boxedLetter = Character(y) [source]
      Leaf.boxedCount = Integer(3300) [source]
      Leaf.boxedBig = Long(3400) [source]
      Leaf.boxedRatio = Float(35.5) [source]
      Leaf.boxedPrecise = Double(36.5) [source]
      Leaf.text = String(leaf-loaded) [source]
      Leaf.mode = Mode(FAST) [source]
      Leaf.point = Point(Point[x=7, y=8]) [source]
      Leaf.numbers = [Integer(7), Integer(8), Integer(9)] [source]
      Leaf.names = [String(x), String(y)] [source]
      Leaf.grid = [[Integer(9)]] [source]
      Leaf.list = List([x, y]) [source]
      Leaf.set = Set([x, y]) [source]
      Leaf.map = Map({x=9}) [source]
      Leaf.nestedGeneric = Map({x=[SLOW, FAST]}) [source]
      Leaf.nested = Nested{depth=Integer(2), label=String(nested-loaded), tags=List([x])} [source]
      Leaf.box = Box{value=String(box-loaded)} [source]
      Leaf.any = Integer(4242) [source]
      Mid.midRatio = Double(0.75) [source]
      Mid.midWorlds = List([world, world_nether]) [source]
      Mid.midBoxed = Integer(701) [source]
      Base.baseStatic = String(base-static) [kept] [source]
      Base.basePrivate = Integer(2) [source]
      Base.baseProtected = String(base-protected-loaded) [source]
      Base.basePackage = String(base-package-loaded) [source]
      Base.basePublic = Long(20) [source]
      Base.baseFinal = String(target-final) [kept]
      Base.baseTransient = String(base-transient) [kept]

      scenario=syntheticNonFinalField
      outcome=ok
      exception=none
      SyntheticHolder.syn$value = String(syn-loaded) [source]
      SyntheticHolder.plainValue = String(plain-loaded) [source]

      scenario=jdkClassWithEncapsulatedFields
      outcome=throws
      exception=java.lang.RuntimeException <- java.lang.reflect.InaccessibleObjectException

      scenario=emptyClass
      outcome=ok
      exception=none

      scenario=plainObjectTarget
      outcome=ok
      exception=none

      scenario=nullSourceWithFieldsOnTarget
      outcome=throws
      exception=java.lang.NullPointerException
      Leaf.LEAF_CONSTANT = String(leaf-constant) [kept]
      Leaf.leafStatic = Integer(9) [kept]
      Leaf.leafStaticTransient = String(leaf-static-transient) [kept]
      Leaf.leafFinal = Integer(11) [kept]
      Leaf.leafFinalList = List([final]) [kept]
      Leaf.leafTransient = Integer(12) [kept]
      Leaf.leafTransientList = List([transient]) [kept]
      Leaf.flag = Boolean(false) [kept]
      Leaf.tiny = Byte(1) [kept]
      Leaf.small = Short(2) [kept]
      Leaf.letter = Character(a) [kept]
      Leaf.count = Integer(3) [kept]
      Leaf.big = Long(4) [kept]
      Leaf.ratio = Float(1.5) [kept]
      Leaf.precise = Double(2.5) [kept]
      Leaf.volatileCount = Integer(5) [kept]
      Leaf.boxedFlag = Boolean(false) [kept]
      Leaf.boxedTiny = Byte(11) [kept]
      Leaf.boxedSmall = Short(1200) [kept]
      Leaf.boxedLetter = Character(b) [kept]
      Leaf.boxedCount = Integer(300) [kept]
      Leaf.boxedBig = Long(400) [kept]
      Leaf.boxedRatio = Float(15.5) [kept]
      Leaf.boxedPrecise = Double(25.5) [kept]
      Leaf.text = String(leaf) [kept]
      Leaf.mode = Mode(OFF) [kept]
      Leaf.point = Point(Point[x=1, y=2]) [kept]
      Leaf.numbers = [Integer(1), Integer(2)] [kept]
      Leaf.names = [String(n)] [kept]
      Leaf.grid = [[Integer(1)], [Integer(2), Integer(3)]] [kept]
      Leaf.list = List([l]) [kept]
      Leaf.set = Set([s]) [kept]
      Leaf.map = Map({m=1}) [kept]
      Leaf.nestedGeneric = Map({g=[OFF]}) [kept]
      Leaf.nested = Nested{depth=Integer(1), label=String(nested-default), tags=List([a])} [kept]
      Leaf.box = Box{value=String(box)} [kept]
      Leaf.any = String(any) [kept]
      Mid.midRatio = Double(0.5) [kept]
      Mid.midWorlds = List([mid]) [kept]
      Mid.midBoxed = Integer(700) [kept]
      Base.baseStatic = String(base-static) [kept]
      Base.basePrivate = Integer(1) [kept]
      Base.baseProtected = String(base-protected) [kept]
      Base.basePackage = String(base-package) [kept]
      Base.basePublic = Long(10) [kept]
      Base.baseFinal = String(target-final) [kept]
      Base.baseTransient = String(base-transient) [kept]

      scenario=nullSourceWithNoCopyableFieldsOnTarget
      outcome=throws
      exception=java.lang.NullPointerException
      RecordConfig.amount = Integer(1) [kept]
      RecordConfig.label = String(current) [kept]

      scenario=nullTarget
      outcome=throws
      exception=java.lang.NullPointerException
      target=null
      """;

  private CurseRecordedBehavior() {
  }

  static Map<String, ConfigurationFieldScenarios.Outcome> outcomes() {
    Map<String, ConfigurationFieldScenarios.Outcome> outcomes = new LinkedHashMap<>();
    for (String block : RECORDING.strip().split("\n\n")) {
      List<String> lines = new ArrayList<>(List.of(block.split("\n")));
      String name = lines.remove(0).substring("scenario=".length());
      boolean threw = lines.remove(0).equals("outcome=throws");
      String exception = lines.remove(0).substring("exception=".length());
      outcomes.put(name, new ConfigurationFieldScenarios.Outcome(name, threw, exception, lines));
    }
    return outcomes;
  }
}
