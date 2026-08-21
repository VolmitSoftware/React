package art.arcane.react.util.director;

import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.director.context.DirectorContextBase;

public class DirectorContext {
  private static final DirectorContextBase<VolmitSender> context = new DirectorContextBase<>();

  public static VolmitSender get() {
    return context.get();
  }

  public static void touch(VolmitSender c) {
    context.touch(c);
  }

  public static void remove() {
    context.remove();
  }
}
