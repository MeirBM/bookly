package com.bookly.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Captures the application's log output around an action.
 *
 * <p>The levels are the ones criterion 1.9 fixes: the root logger at INFO, which is what a
 * deployment runs at, and every {@code com.bookly} logger at TRACE, so application code is examined
 * at its most verbose. Deliberately not the whole world at TRACE — at that level the servlet
 * container echoes the raw request body by construction, which no implementation could prevent.
 */
public final class LogCapture {

    /** Emitted at the end of every capture, so an empty result cannot pass as "nothing was logged". */
    public static final String PROBE = "log-capture-probe-4d1a";

    private LogCapture() {}

    @FunctionalInterface
    public interface Action {
        void run() throws Exception;
    }

    public static List<ILoggingEvent> around(Action action) throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Logger application = (Logger) LoggerFactory.getLogger("com.bookly");
        Level previousRoot = root.getLevel();
        Level previousApplication = application.getLevel();
        Appender<ILoggingEvent> console = root.getAppender("STDOUT");
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);
        if (console != null) {
            root.detachAppender(console);
        }
        root.setLevel(Level.INFO);
        application.setLevel(Level.TRACE);
        try {
            action.run();
            LoggerFactory.getLogger("com.bookly.logcapture.probe").trace(PROBE);
        } finally {
            root.setLevel(previousRoot);
            application.setLevel(previousApplication);
            root.detachAppender(captured);
            if (console != null) {
                root.addAppender(console);
            }
        }
        return List.copyOf(captured.list);
    }

    /** Everything an event could put in front of a reader: message, MDC and any throwable message. */
    public static String render(ILoggingEvent event) {
        return event.getFormattedMessage()
                + " " + event.getMDCPropertyMap()
                + " " + (event.getThrowableProxy() == null ? "" : event.getThrowableProxy().getMessage());
    }
}
