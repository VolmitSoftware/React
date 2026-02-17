package art.arcane.react.util.decree;

import art.arcane.volmlib.util.director.context.DirectorContextHandlers;
import art.arcane.volmlib.util.director.context.DirectorContextHandlerType;
import art.arcane.react.React;
import art.arcane.react.util.plugin.VolmitSender;

import java.util.Map;

public interface DecreeContextHandler<T> extends DirectorContextHandlerType<T, VolmitSender> {
    Map<Class<?>, DecreeContextHandler<?>> contextHandlers = buildContextHandlers();

    static Map<Class<?>, DecreeContextHandler<?>> buildContextHandlers() {
        return DirectorContextHandlers.buildOrEmpty(
                React.initialize("art.arcane.react.util.decree.context"),
                DecreeContextHandler.class,
                h -> ((DecreeContextHandler<?>) h).getType(),
                Throwable::printStackTrace);
    }
}
