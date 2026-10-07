package moe.kyokobot.libdave;

public class KeyRatchetTest extends KeyRatchetTestBase {
    @Override
    DaveFactory getDaveFactory() {
        FfmDaveFactory.ensureAvailable();
        return new FfmDaveFactory();
    }
}
