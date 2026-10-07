package moe.kyokobot.libdave;

import moe.kyokobot.libdave.TestGroup.Member;
import org.junit.jupiter.api.Test;

import static moe.kyokobot.libdave.TestGroup.USER_A;
import static moe.kyokobot.libdave.TestGroup.USER_B;
import static org.junit.jupiter.api.Assertions.*;

public abstract class KeyRatchetTestBase {
    private static final int AES_GCM_128_KEY_BYTES = 16;

    abstract DaveFactory getDaveFactory();

    /**
     * A {@link KeyRatchet} from outside the implementation under test.
     */
    static final class ForeignKeyRatchet implements KeyRatchet {
        boolean closed;

        @Override
        public byte[] getEncryptionKey(int keyGeneration) {
            return new byte[AES_GCM_128_KEY_BYTES];
        }

        @Override
        public void deleteKey(int keyGeneration) {
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    public void keysAreDeterministicPerGeneration() throws Exception {
        try (TestGroup group = new TestGroup(getDaveFactory())) {
            Member a = group.establish()[0];
            try (KeyRatchet ratchet = a.session.getKeyRatchet(USER_A)) {
                assertNotNull(ratchet);
                byte[] key0 = ratchet.getEncryptionKey(0);
                byte[] key1 = ratchet.getEncryptionKey(1);

                assertEquals(AES_GCM_128_KEY_BYTES, key0.length);
                assertEquals(AES_GCM_128_KEY_BYTES, key1.length);
                assertArrayEquals(key0, ratchet.getEncryptionKey(0));
                assertFalse(java.util.Arrays.equals(key0, key1));
            }
        }
    }

    @Test
    public void membersDeriveTheSameKeysPerSender() throws Exception {
        try (TestGroup group = new TestGroup(getDaveFactory())) {
            Member[] members = group.establish();
            try (KeyRatchet aOnA = members[0].session.getKeyRatchet(USER_A);
                 KeyRatchet aOnB = members[1].session.getKeyRatchet(USER_A);
                 KeyRatchet bOnA = members[0].session.getKeyRatchet(USER_B)) {
                assertArrayEquals(aOnA.getEncryptionKey(0), aOnB.getEncryptionKey(0));
                assertFalse(java.util.Arrays.equals(aOnA.getEncryptionKey(0), bOnA.getEncryptionKey(0)));
            }
        }
    }

    @Test
    public void deletedKeyIsGone() throws Exception {
        try (TestGroup group = new TestGroup(getDaveFactory())) {
            Member a = group.establish()[0];
            try (KeyRatchet ratchet = a.session.getKeyRatchet(USER_A)) {
                ratchet.getEncryptionKey(0);
                ratchet.deleteKey(0);

                assertEquals(0, ratchet.getEncryptionKey(0).length);
                assertEquals(AES_GCM_128_KEY_BYTES, ratchet.getEncryptionKey(1).length);
            }
        }
    }

    @Test
    public void closedRatchetRejectsCalls() throws Exception {
        try (TestGroup group = new TestGroup(getDaveFactory())) {
            Member a = group.establish()[0];
            KeyRatchet ratchet = a.session.getKeyRatchet(USER_A);
            ratchet.close();
            ratchet.close();

            assertThrows(IllegalStateException.class, () -> ratchet.getEncryptionKey(0));
            assertThrows(IllegalStateException.class, () -> ratchet.deleteKey(0));
        }
    }

    @Test
    public void encryptorTakesOwnershipOfRatchet() throws Exception {
        DaveFactory factory = getDaveFactory();
        try (TestGroup group = new TestGroup(factory);
             Encryptor encryptor = factory.createEncryptor()) {
            Member a = group.establish()[0];
            KeyRatchet ratchet = a.session.getKeyRatchet(USER_A);

            encryptor.setKeyRatchet(ratchet);
            assertThrows(IllegalStateException.class, () -> ratchet.getEncryptionKey(0));
            // Closing a handed-over ratchet must not free it under the encryptor.
            ratchet.close();
            assertThrows(IllegalStateException.class, () -> encryptor.setKeyRatchet(ratchet));
            assertEquals(1, encryptor.getProtocolVersion());
        }
    }

    @Test
    public void decryptorTakesOwnershipOfRatchet() throws Exception {
        DaveFactory factory = getDaveFactory();
        try (TestGroup group = new TestGroup(factory);
             Decryptor decryptor = factory.createDecryptor()) {
            Member b = group.establish()[1];
            KeyRatchet ratchet = b.session.getKeyRatchet(USER_A);

            decryptor.transitionToKeyRatchet(ratchet);
            assertThrows(IllegalStateException.class, () -> ratchet.getEncryptionKey(0));
            ratchet.close();
            assertThrows(IllegalStateException.class, () -> decryptor.transitionToKeyRatchet(ratchet));
        }
    }

    @Test
    public void foreignRatchetIsRejected() {
        DaveFactory factory = getDaveFactory();
        ForeignKeyRatchet ratchet = new ForeignKeyRatchet();
        try (Encryptor encryptor = factory.createEncryptor();
             Decryptor decryptor = factory.createDecryptor()) {
            assertThrows(IllegalArgumentException.class, () -> encryptor.setKeyRatchet(ratchet));
            assertThrows(IllegalArgumentException.class, () -> decryptor.transitionToKeyRatchet(ratchet));
            assertFalse(ratchet.closed);
        }
    }
}
