package art.arcane.react.api.web;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.Message;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.UUID;

public final class Log4jConsoleCapture implements AutoCloseable {

    private static final String APPENDER_CLASS = "org.apache.logging.log4j.core.Appender";
    private static final String LOG_EVENT_CLASS = "org.apache.logging.log4j.core.LogEvent";
    private static final String LIFE_CYCLE_STATE_CLASS = "org.apache.logging.log4j.core.LifeCycle$State";

    private final Object rootLogger;
    private final Object appender;
    private final Method removeAppender;
    private final AppenderInvocation invocation;
    private boolean closed;

    private Log4jConsoleCapture(
        Object rootLogger,
        Object appender,
        Method removeAppender,
        AppenderInvocation invocation
    ) {
        this.rootLogger = rootLogger;
        this.appender = appender;
        this.removeAppender = removeAppender;
        this.invocation = invocation;
        this.closed = false;
    }

    public static Log4jConsoleCapture attach(RingLogHandler handler) throws ReflectiveOperationException {
        Objects.requireNonNull(handler, "handler");
        Logger rootLogger = LogManager.getRootLogger();
        ClassLoader classLoader = rootLogger.getClass().getClassLoader();
        Class<?> appenderClass = Class.forName(APPENDER_CLASS, false, classLoader);
        Class<?> logEventClass = Class.forName(LOG_EVENT_CLASS, false, classLoader);
        Class<?> lifeCycleStateClass = Class.forName(LIFE_CYCLE_STATE_CLASS, false, classLoader);
        AppenderInvocation invocation = new AppenderInvocation(handler, logEventClass, lifeCycleStateClass);
        Object appender = Proxy.newProxyInstance(
            appenderClass.getClassLoader(),
            new Class<?>[]{appenderClass},
            invocation
        );
        Method addAppender = rootLogger.getClass().getMethod("addAppender", appenderClass);
        Method removeAppender = rootLogger.getClass().getMethod("removeAppender", appenderClass);
        invocation.start();
        addAppender.invoke(rootLogger, appender);
        return new Log4jConsoleCapture(rootLogger, appender, removeAppender, invocation);
    }

    @Override
    public synchronized void close() throws ReflectiveOperationException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            removeAppender.invoke(rootLogger, appender);
        } finally {
            invocation.stop();
        }
    }

    private static final class AppenderInvocation implements InvocationHandler {

        private final RingLogHandler handler;
        private final Method eventGetLevel;
        private final Method eventGetMessage;
        private final Method eventGetThrown;
        private final Object initializedState;
        private final Object startedState;
        private final Object stoppedState;
        private final String name;
        private volatile Object state;

        private AppenderInvocation(
            RingLogHandler handler,
            Class<?> logEventClass,
            Class<?> lifeCycleStateClass
        ) throws NoSuchMethodException {
            this.handler = handler;
            this.eventGetLevel = logEventClass.getMethod("getLevel");
            this.eventGetMessage = logEventClass.getMethod("getMessage");
            this.eventGetThrown = logEventClass.getMethod("getThrown");
            this.initializedState = enumConstant(lifeCycleStateClass, "INITIALIZED");
            this.startedState = enumConstant(lifeCycleStateClass, "STARTED");
            this.stoppedState = enumConstant(lifeCycleStateClass, "STOPPED");
            this.name = "react-web-console-" + UUID.randomUUID();
            this.state = initializedState;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            if (methodName.equals("append")) {
                append(args == null || args.length == 0 ? null : args[0]);
                return null;
            }
            if (methodName.equals("getName")) {
                return name;
            }
            if (methodName.equals("getState")) {
                return state;
            }
            if (methodName.equals("initialize")) {
                state = initializedState;
                return null;
            }
            if (methodName.equals("start")) {
                start();
                return null;
            }
            if (methodName.equals("stop")) {
                stop();
                return null;
            }
            if (methodName.equals("isStarted")) {
                return state == startedState;
            }
            if (methodName.equals("isStopped")) {
                return state == stoppedState;
            }
            if (methodName.equals("ignoreExceptions")) {
                return true;
            }
            if (methodName.equals("equals")) {
                return args != null && args.length == 1 && proxy == args[0];
            }
            if (methodName.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (methodName.equals("toString")) {
                return name;
            }
            return defaultValue(method.getReturnType());
        }

        private void append(Object event) {
            if (event == null || state != startedState) {
                return;
            }
            try {
                Object levelValue = eventGetLevel.invoke(event);
                Object messageValue = eventGetMessage.invoke(event);
                Object thrownValue = eventGetThrown.invoke(event);
                String level = levelValue == null ? "INFO" : levelValue.toString();
                String message = formattedMessage(messageValue);
                Throwable thrown = thrownValue instanceof Throwable ? (Throwable) thrownValue : null;
                handler.publishExternal(level, message, thrown);
            } catch (ReflectiveOperationException e) {
                handler.publishExternal("ERROR", "Unable to capture a server console record", e);
            }
        }

        private void start() {
            state = startedState;
        }

        private void stop() {
            state = stoppedState;
        }

        private static String formattedMessage(Object messageValue) {
            if (messageValue instanceof Message) {
                Message message = (Message) messageValue;
                return message.getFormattedMessage();
            }
            return messageValue == null ? "" : messageValue.toString();
        }

        private static Object enumConstant(Class<?> enumClass, String name) {
            Object[] constants = enumClass.getEnumConstants();
            if (constants == null) {
                throw new IllegalArgumentException(enumClass.getName() + " is not an enum");
            }
            for (Object constant : constants) {
                if (constant instanceof Enum && ((Enum<?>) constant).name().equals(name)) {
                    return constant;
                }
            }
            throw new IllegalArgumentException("Missing lifecycle state " + name);
        }

        private static Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive() || returnType == void.class) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == char.class) {
                return '\0';
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0.0F;
            }
            return 0.0D;
        }
    }
}
