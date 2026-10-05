package moe.kyokobot.libdave;

public class SessionTest extends SessionTestBase {
    @Override
    DaveFactory getDaveFactory() {
        NativeDaveFactory.ensureAvailable();
        return new NativeDaveFactory();
    }
}
