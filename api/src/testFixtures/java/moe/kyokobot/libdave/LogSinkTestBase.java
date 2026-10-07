package moe.kyokobot.libdave;

import moe.kyokobot.libdave.callbacks.DaveLogSink;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public abstract class LogSinkTestBase {
    abstract DaveFactory getDaveFactory();

    abstract void setLogSink(DaveLogSink sink);

    private void triggerLogs(DaveFactory factory) {
        try (Session session = factory.createSession("", "auth-session", (source, reason) -> {
        })) {
            session.init(1, 1234L, "111");
            session.processCommit(new byte[]{1, 2, 3, 4});
        }
    }

    @Test
    public void logSinkReceivesMessages() {
        DaveFactory factory = getDaveFactory();
        List<String> messages = new CopyOnWriteArrayList<>();
        try {
            setLogSink((severity, file, line, message) -> messages.add(message));
            triggerLogs(factory);
            assertFalse(messages.isEmpty(), "no log messages received");

            setLogSink(null);
            int count = messages.size();
            triggerLogs(factory);
            assertEquals(count, messages.size());
        } finally {
            setLogSink(null);
        }
    }

    @Test
    public void messagesAreWellFormed() {
        DaveFactory factory = getDaveFactory();
        List<Object[]> messages = new CopyOnWriteArrayList<>();
        try {
            setLogSink((severity, file, line, message) -> messages.add(new Object[]{severity, file, line, message}));
            triggerLogs(factory);
        } finally {
            setLogSink(null);
        }

        assertFalse(messages.isEmpty());
        for (Object[] message : messages) {
            int severity = (Integer) message[0];
            assertTrue(severity >= 0 && severity <= 3, "unexpected severity " + severity);
            assertNotNull(message[1]);
            assertNotNull(message[3]);
            assertFalse(((String) message[3]).isEmpty());
        }
    }

    @Test
    public void replacedSinkStopsReceiving() {
        DaveFactory factory = getDaveFactory();
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        try {
            setLogSink((severity, file, line, message) -> first.incrementAndGet());
            setLogSink((severity, file, line, message) -> second.incrementAndGet());
            triggerLogs(factory);
        } finally {
            setLogSink(null);
        }

        assertEquals(0, first.get());
        assertTrue(second.get() > 0);
    }

    @Test
    public void throwingSinkDoesNotPropagate() {
        DaveFactory factory = getDaveFactory();
        AtomicInteger calls = new AtomicInteger();
        try {
            setLogSink((severity, file, line, message) -> {
                calls.incrementAndGet();
                throw new IllegalStateException("thrown from the log sink on purpose");
            });
            triggerLogs(factory);
            assertTrue(calls.get() > 0);

            // Native code keeps working after the sink threw.
            try (Session session = factory.createSession("", "", null)) {
                session.init(1, 1234L, "111");
                assertTrue(session.getMarshalledKeyPackage().length > 0);
            }
        } finally {
            setLogSink(null);
        }
    }

    @Test
    public void sinkCanBeSwappedWhileLogging() throws Exception {
        DaveFactory factory = getDaveFactory();
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] loggers = new Thread[4];
        for (int i = 0; i < loggers.length; i++) {
            loggers[i] = new Thread(() -> {
                try {
                    while (running.get()) {
                        triggerLogs(factory);
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }, "dave-logger-" + i);
            loggers[i].start();
        }

        AtomicInteger received = new AtomicInteger();
        try {
            long deadline = System.nanoTime() + 500_000_000L;
            for (int i = 0; System.nanoTime() < deadline; i++) {
                setLogSink(i % 3 == 2 ? null : (severity, file, line, message) -> received.incrementAndGet());
            }
        } finally {
            running.set(false);
            for (Thread logger : loggers) {
                logger.join(30_000);
            }
            setLogSink(null);
        }

        assertNull(failure.get());
        assertTrue(received.get() > 0);
    }
}
