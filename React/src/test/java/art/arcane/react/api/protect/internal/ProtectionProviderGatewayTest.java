package art.arcane.react.api.protect.internal;

import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactProtectionProvider;
import art.arcane.react.api.protect.ReactProtectionRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

class ProtectionProviderGatewayTest {

  private static ProtectionProviderGateway gateway(List<String> sink) {
    return new ProtectionProviderGateway(3, 0L, sink::add);
  }

  private static ReactProtectionProvider provider(String id, List<ReactProtectionRule> rules) {
    return new ReactProtectionProvider() {
      @Override
      public List<ReactProtectionRule> rules() {
        return rules;
      }

      @Override
      public String providerId() {
        return id;
      }
    };
  }

  @Test
  void wellBehavedProviderIsAccepted() {
    List<String> log = new ArrayList<>();
    ProtectionProviderGateway gateway = gateway(log);
    ProtectionProviderGateway.Collected collected = gateway.collect(
        provider("pets", List.of(ReactProtectionRule.of("owned", ReactOperation.STACK))), "GuardianPets");

    Assertions.assertEquals("pets", collected.providerId());
    Assertions.assertNotNull(collected.declaration());
    Assertions.assertEquals(1, collected.declaration().rules().size());
    Assertions.assertEquals(0L, gateway.totalFaults());
    Assertions.assertTrue(log.isEmpty());
  }

  @Test
  void providerWhoseRulesThrowIsCountedAsAFaultAndRefusesQuietly() {
    List<String> log = new ArrayList<>();
    ProtectionProviderGateway gateway = gateway(log);
    ProtectionProviderGateway.Collected collected = gateway.collect(new ReactProtectionProvider() {
      @Override
      public List<ReactProtectionRule> rules() {
        throw new IllegalStateException("boom");
      }

      @Override
      public String providerId() {
        return "hostile";
      }
    }, "Hostile");

    Assertions.assertEquals("hostile", collected.providerId());
    Assertions.assertNull(collected.declaration());
    Assertions.assertEquals(1L, gateway.totalFaults());
    Assertions.assertEquals(1, gateway.faultCount("hostile"));
    Assertions.assertTrue(log.getFirst().contains("Hostile"));
    Assertions.assertTrue(log.getFirst().contains("IllegalStateException"));
  }

  @Test
  void providerReturningNullRulesIsAFault() {
    List<String> log = new ArrayList<>();
    ProtectionProviderGateway gateway = gateway(log);
    ProtectionProviderGateway.Collected collected = gateway.collect(provider("nully", null), "Nully");

    Assertions.assertNull(collected.declaration());
    Assertions.assertEquals(1L, gateway.totalFaults());
  }

  @Test
  void repeatedFaultsQuarantineTheProviderAndNameThePlugin() {
    List<String> log = new ArrayList<>();
    ProtectionProviderGateway gateway = gateway(log);
    ReactProtectionProvider hostile = provider("hostile", null);

    for (int i = 0; i < 3; i++) {
      gateway.collect(hostile, "Hostile");
    }

    Assertions.assertTrue(gateway.isQuarantined("hostile"));
    Assertions.assertTrue(gateway.quarantinedProviders().contains("hostile"));
    Assertions.assertTrue(log.stream().anyMatch(line -> line.contains("quarantined") && line.contains("Hostile")));

    int faultsBefore = gateway.faultCount("hostile");
    ProtectionProviderGateway.Collected collected = gateway.collect(hostile, "Hostile");
    Assertions.assertNull(collected.declaration());
    Assertions.assertEquals("hostile", collected.providerId());
    Assertions.assertEquals(faultsBefore, gateway.faultCount("hostile"));
  }

  @Test
  void quarantineIsClearedWhenTheProviderIsForgotten() {
    ProtectionProviderGateway gateway = gateway(new ArrayList<>());
    ReactProtectionProvider hostile = provider("hostile", null);

    for (int i = 0; i < 3; i++) {
      gateway.collect(hostile, "Hostile");
    }

    Assertions.assertTrue(gateway.isQuarantined("hostile"));
    gateway.forget("hostile");
    Assertions.assertFalse(gateway.isQuarantined("hostile"));
    Assertions.assertEquals(0, gateway.faultCount("hostile"));
  }

  @Test
  void providerIdThatThrowsIsRefusedWithoutCallingRules() {
    List<String> log = new ArrayList<>();
    ProtectionProviderGateway gateway = gateway(log);
    boolean[] rulesCalled = {false};

    ProtectionProviderGateway.Collected collected = gateway.collect(new ReactProtectionProvider() {
      @Override
      public List<ReactProtectionRule> rules() {
        rulesCalled[0] = true;
        return List.of();
      }

      @Override
      public String providerId() {
        throw new IllegalArgumentException("nope");
      }
    }, "Broken");

    Assertions.assertEquals("", collected.providerId());
    Assertions.assertNull(collected.declaration());
    Assertions.assertFalse(rulesCalled[0]);
    Assertions.assertEquals(1L, gateway.totalRefusals());
  }

  @Test
  void blankProviderIdIsRefused() {
    List<String> log = new ArrayList<>();
    ProtectionProviderGateway gateway = gateway(log);
    ProtectionProviderGateway.Collected collected = gateway.collect(provider("  ", List.of()), "Blank");

    Assertions.assertEquals("", collected.providerId());
    Assertions.assertEquals(1L, gateway.totalRefusals());
    Assertions.assertTrue(log.getFirst().contains("blank providerId"));
  }

  @Test
  void syntheticProviderIdIsWarnedAboutOnceButStillAccepted() {
    List<String> log = new ArrayList<>();
    ProtectionProviderGateway gateway = gateway(log);
    ReactProtectionProvider lambda = provider(
        "com.example.Rules$$Lambda/0x00007f2a", List.of(ReactProtectionRule.of("r", ReactOperation.TRIM)));

    Assertions.assertNotNull(gateway.collect(lambda, "Example").declaration());
    Assertions.assertNotNull(gateway.collect(lambda, "Example").declaration());
    Assertions.assertEquals(1, log.stream().filter(line -> line.contains("synthetic providerId")).count());
  }

  @Test
  void ruleListIsCappedAndNullRulesAreSkipped() {
    List<String> log = new ArrayList<>();
    ProtectionProviderGateway gateway = gateway(log);
    List<ReactProtectionRule> rules = new ArrayList<>();
    rules.add(null);

    for (int i = 0; i < ProtectionProviderGateway.MAX_RULES_PER_PROVIDER + 5; i++) {
      rules.add(ReactProtectionRule.of("r" + i, ReactOperation.TRIM));
    }

    ProtectionDeclaration declaration = gateway.collect(
        provider("many", Collections.unmodifiableList(rules)), "Many").declaration();

    Assertions.assertEquals(ProtectionProviderGateway.MAX_RULES_PER_PROVIDER, declaration.rules().size());
    Assertions.assertTrue(log.stream().anyMatch(line -> line.contains("declared more than")));
  }

  @Test
  void aRuleListWhoseIteratorThrowsIsContainedAsAFault() {
    List<String> log = new ArrayList<>();
    ProtectionProviderGateway gateway = gateway(log);
    List<ReactProtectionRule> hostile = new AbstractList<>() {
      @Override
      public ReactProtectionRule get(int index) {
        throw new IllegalStateException("get() is hostile");
      }

      @Override
      public int size() {
        return 1;
      }

      @Override
      public Iterator<ReactProtectionRule> iterator() {
        throw new IllegalStateException("iterator() is hostile");
      }
    };

    ProtectionProviderGateway.Collected collected = gateway.collect(provider("hostile", hostile), "Hostile");

    Assertions.assertEquals("hostile", collected.providerId());
    Assertions.assertNull(collected.declaration());
    Assertions.assertEquals(1L, gateway.totalFaults());
    Assertions.assertTrue(log.getFirst().contains("IllegalStateException"));
  }

  @Test
  void aRuleListWhoseSizeThrowsIsNeverAsked() {
    List<String> log = new ArrayList<>();
    ProtectionProviderGateway gateway = gateway(log);
    ReactProtectionRule rule = ReactProtectionRule.of("owned", ReactOperation.STACK);
    List<ReactProtectionRule> hostile = new AbstractList<>() {
      @Override
      public ReactProtectionRule get(int index) {
        return rule;
      }

      @Override
      public int size() {
        throw new IllegalStateException("size() is hostile");
      }

      @Override
      public Iterator<ReactProtectionRule> iterator() {
        return List.of(rule).iterator();
      }
    };

    ProtectionProviderGateway.Collected collected = gateway.collect(provider("pets", hostile), "GuardianPets");

    Assertions.assertNotNull(collected.declaration());
    Assertions.assertEquals(List.of(rule), collected.declaration().rules());
    Assertions.assertEquals(0L, gateway.totalFaults());
    Assertions.assertTrue(log.isEmpty());
  }

  @Test
  void anEndlessRuleListIsDrainedNoFurtherThanTheCap() {
    List<String> log = new ArrayList<>();
    ProtectionProviderGateway gateway = gateway(log);
    List<ReactProtectionRule> endless = new AbstractList<>() {
      @Override
      public ReactProtectionRule get(int index) {
        return ReactProtectionRule.of("r" + index, ReactOperation.TRIM);
      }

      @Override
      public int size() {
        return Integer.MAX_VALUE;
      }
    };

    ProtectionDeclaration declaration = gateway.collect(provider("endless", endless), "Endless").declaration();

    Assertions.assertEquals(ProtectionProviderGateway.MAX_RULES_PER_PROVIDER, declaration.rules().size());
    Assertions.assertTrue(log.stream().anyMatch(line -> line.contains("declared more than")));
  }

  @Test
  void nullProviderIsRefusedWithoutAnId() {
    ProtectionProviderGateway gateway = gateway(new ArrayList<>());
    Assertions.assertEquals("", gateway.collect(null, "Nobody").providerId());
  }

  @Test
  void slowProviderIsWarnedAboutOnce() {
    List<String> log = new ArrayList<>();
    ProtectionProviderGateway gateway = new ProtectionProviderGateway(5, 1L, log::add);
    ReactProtectionProvider slow = new ReactProtectionProvider() {
      @Override
      public List<ReactProtectionRule> rules() {
        long until = System.nanoTime() + 3_000_000L;

        while (System.nanoTime() < until) {
          Thread.onSpinWait();
        }

        return List.of(ReactProtectionRule.of("r", ReactOperation.TRIM));
      }

      @Override
      public String providerId() {
        return "slow";
      }
    };

    Assertions.assertNotNull(gateway.collect(slow, "Slow").declaration());
    Assertions.assertNotNull(gateway.collect(slow, "Slow").declaration());
    Assertions.assertEquals(1, log.stream().filter(line -> line.contains("do not block")).count());
  }
}
