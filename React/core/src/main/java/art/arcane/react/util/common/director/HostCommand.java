package art.arcane.react.util.director;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@link art.arcane.react.platform.CommandHost} method a command runs. Mod loaders hide the command from help and
 * tab completion when their command host does not implement that method.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface HostCommand {
  String value();
}
