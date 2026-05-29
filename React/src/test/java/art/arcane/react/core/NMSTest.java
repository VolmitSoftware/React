package art.arcane.react.core;

import org.junit.Assert;
import org.junit.Test;

public class NMSTest {
  @Test
  public void classForReturnsFirstMatchingClass() {
    Class<?> resolvedClass = NMS.classFor("java.lang.Integer", "java.lang.String");
    Assert.assertEquals(Integer.class, resolvedClass);
  }

  @Test
  public void classForFallsBackToLaterMatches() {
    Class<?> resolvedClass = NMS.classFor("does.not.Exist", "java.lang.String");
    Assert.assertEquals(String.class, resolvedClass);
  }

  @Test
  public void classForReturnsNullWhenNoCandidatesResolve() {
    Class<?> resolvedClass = NMS.classFor("does.not.Exist.One", "does.not.Exist.Two");
    Assert.assertNull(resolvedClass);
  }
}
