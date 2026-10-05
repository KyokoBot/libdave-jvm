package moe.kyokobot.libdave;

import moe.kyokobot.libdave.callbacks.DaveLogSink;

public class LogSinkTest extends LogSinkTestBase {
    @Override
    DaveFactory getDaveFactory() {
        NativeDaveFactory.ensureAvailable();
        return new NativeDaveFactory();
    }

    @Override
    void setLogSink(DaveLogSink sink) {
        NativeDaveFactory.setLogSink(sink);
    }
}
