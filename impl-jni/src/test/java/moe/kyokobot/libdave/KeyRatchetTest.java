package moe.kyokobot.libdave;

public class KeyRatchetTest extends KeyRatchetTestBase {
    @Override
    DaveFactory getDaveFactory() {
        NativeDaveFactory.ensureAvailable();
        return new NativeDaveFactory();
    }
}
