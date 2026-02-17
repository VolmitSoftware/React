package art.arcane.react.util.director;

import art.arcane.volmlib.util.director.context.DirectorContextHandlers;
import art.arcane.volmlib.util.director.context.DirectorContextHandlerType;
import art.arcane.react.React;
import art.arcane.react.util.plugin.VolmitSender;

import java.util.Map;

public interface DirectorContextHandler<T> extends DirectorContextHandlerType<T, VolmitSender> {
    Map<Class<?>, DirectorContextHandler<?>> contextHandlers = buildContextHandlers();

    static Map<Class<?>, DirectorContextHandler<?>> buildContextHandlers() {
        return DirectorContextHandlers.buildOrEmpty(
                React.initialize("art.arcane.react.util.director.context"),
                DirectorContextHandler.class,
                h -> ((DirectorContextHandler<?>) h).getType(),
                Throwable::printStackTrace);
    }
}
