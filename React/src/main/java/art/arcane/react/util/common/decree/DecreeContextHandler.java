package art.arcane.react.util.decree;

import art.arcane.volmlib.util.decree.context.DecreeContextHandlers;
import art.arcane.volmlib.util.decree.context.DecreeContextHandlerType;
import art.arcane.react.React;
import art.arcane.react.util.plugin.VolmitSender;

import java.util.Map;

public interface DecreeContextHandler<T> extends DecreeContextHandlerType<T, VolmitSender> {
    Map<Class<?>, DecreeContextHandler<?>> contextHandlers = buildContextHandlers();

    static Map<Class<?>, DecreeContextHandler<?>> buildContextHandlers() {
        return DecreeContextHandlers.buildOrEmpty(
                React.initialize("art.arcane.react.util.decree.context"),
                DecreeContextHandler.class,
                h -> ((DecreeContextHandler<?>) h).getType(),
                Throwable::printStackTrace);
    }
}
