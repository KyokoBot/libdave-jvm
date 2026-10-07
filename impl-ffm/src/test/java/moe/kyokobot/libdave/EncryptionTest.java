package moe.kyokobot.libdave;

public class EncryptionTest extends EncryptionTestBase {
    @Override
    DaveFactory getDaveFactory() {
        FfmDaveFactory.ensureAvailable();
        return new FfmDaveFactory();
    }
}
