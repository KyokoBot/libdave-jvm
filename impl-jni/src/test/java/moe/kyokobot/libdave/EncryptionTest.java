package moe.kyokobot.libdave;

public class EncryptionTest extends EncryptionTestBase {
    @Override
    DaveFactory getDaveFactory() {
        NativeDaveFactory.ensureAvailable();
        return new NativeDaveFactory();
    }
}
