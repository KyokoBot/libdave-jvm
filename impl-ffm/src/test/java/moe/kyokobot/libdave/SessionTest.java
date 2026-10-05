package moe.kyokobot.libdave;

public class SessionTest extends SessionTestBase {
    @Override
    DaveFactory getDaveFactory() {
        FfmDaveFactory.ensureAvailable();
        return new FfmDaveFactory();
    }
}
