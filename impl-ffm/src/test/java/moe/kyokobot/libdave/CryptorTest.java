package moe.kyokobot.libdave;

public class CryptorTest extends CryptorTestBase {
    @Override
    DaveFactory getDaveFactory() {
        FfmDaveFactory.ensureAvailable();
        return new FfmDaveFactory();
    }
}
