package moe.kyokobot.libdave;

import moe.kyokobot.libdave.callbacks.DaveLogSink;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
