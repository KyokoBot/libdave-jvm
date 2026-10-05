package moe.kyokobot.libdave;

import moe.kyokobot.libdave.callbacks.DaveLogSink;

public class LogSinkTest extends LogSinkTestBase {
    @Override
    DaveFactory getDaveFactory() {
        FfmDaveFactory.ensureAvailable();
        return new FfmDaveFactory();
    }

    @Override
    void setLogSink(DaveLogSink sink) {
        FfmDaveFactory.setLogSink(sink);
    }
}
