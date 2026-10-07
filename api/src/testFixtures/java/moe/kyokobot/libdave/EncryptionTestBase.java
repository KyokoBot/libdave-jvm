package moe.kyokobot.libdave;

import moe.kyokobot.libdave.TestGroup.Member;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static moe.kyokobot.libdave.TestGroup.USER_A;
import static moe.kyokobot.libdave.TestGroup.USER_B;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Encryption with real MLS keys, including the ways it is expected to fail.
 */
public abstract class EncryptionTestBase {
    private static final byte[] FRAME = CryptorTestBase.RANDOM_BYTES;
    private static final int SSRC = 1234;
    private static final byte GUARD = 0x55;
    private static final int GUARD_BYTES = 128;

    abstract DaveFactory getDaveFactory();

    /**
     * An established two-member group, with A sending and B receiving.
     */
    final class Keyed implements AutoCloseable {
        final TestGroup group;
        final Member a;
        final Member b;
        final Encryptor encryptor;
        final Decryptor decryptor;

        Keyed() {
            DaveFactory factory = getDaveFactory();
            group = new TestGroup(factory);
            Member[] members = group.establish();
            a = members[0];
            b = members[1];
            encryptor = factory.createEncryptor();
            encryptor.assignSsrcToCodec(SSRC, Codec.OPUS);
            encryptor.setKeyRatchet(a.session.getKeyRatchet(USER_A));
            decryptor = factory.createDecryptor();
            decryptor.transitionToKeyRatchet(b.session.getKeyRatchet(USER_A));
        }

        byte[] encrypt(byte[] frame) {
            byte[] encrypted = new byte[encryptor.getMaxCiphertextByteSize(MediaType.AUDIO, frame.length)];
            int written = encryptor.encrypt(MediaType.AUDIO, SSRC, frame, encrypted);
            assertTrue(written > 0, "encrypt failed: " + written);
            return Arrays.copyOf(encrypted, written);
        }

        @Override
        public void close() throws Exception {
            decryptor.close();
            encryptor.close();
            group.close();
        }
    }

    private static ByteBuffer guarded(int usable) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(usable + GUARD_BYTES);
        for (int i = 0; i < buffer.capacity(); i++) {
            buffer.put(i, GUARD);
        }
        buffer.limit(usable);
        return buffer;
    }

    private static void assertGuardIntact(ByteBuffer buffer, int usable) {
        ByteBuffer whole = buffer.duplicate();
        whole.clear();
        for (int i = usable; i < whole.capacity(); i++) {
            assertEquals(GUARD, whole.get(i), "native code wrote past the buffer limit at offset " + i);
        }
    }

    @Test
    public void roundTripByteArrays() throws Exception {
        try (Keyed keyed = new Keyed()) {
            byte[] encrypted = keyed.encrypt(FRAME);
            assertTrue(encrypted.length > FRAME.length);
            assertTrue(encrypted.length <= keyed.encryptor.getMaxCiphertextByteSize(MediaType.AUDIO, FRAME.length));
            assertFalse(Arrays.equals(Arrays.copyOf(encrypted, FRAME.length), FRAME), "frame was not encrypted");

            byte[] decrypted = new byte[keyed.decryptor.getMaxPlaintextByteSize(MediaType.AUDIO, encrypted.length)];
            assertEquals(FRAME.length, keyed.decryptor.decrypt(MediaType.AUDIO, encrypted, decrypted));
            assertArrayEquals(FRAME, Arrays.copyOf(decrypted, FRAME.length));
        }
    }

    @Test
    public void roundTripDirectBuffersHonourPositions() throws Exception {
        try (Keyed keyed = new Keyed()) {
            ByteBuffer frame = ByteBuffer.allocateDirect(FRAME.length + 3);
            frame.position(3);
            frame.put(FRAME);
            frame.position(3);

            int maxSize = keyed.encryptor.getMaxCiphertextByteSize(MediaType.AUDIO, FRAME.length);
            ByteBuffer encrypted = ByteBuffer.allocateDirect(maxSize + 5);
            encrypted.position(5);

            int written = keyed.encryptor.encrypt(MediaType.AUDIO, SSRC, frame, encrypted);
            assertTrue(written > 0, "encrypt failed: " + written);
            assertEquals(3, frame.position());
            assertEquals(5, encrypted.position());

            encrypted.limit(5 + written);
            ByteBuffer decrypted = ByteBuffer.allocateDirect(written + 7);
            decrypted.position(7);
            assertEquals(FRAME.length, keyed.decryptor.decrypt(MediaType.AUDIO, encrypted, decrypted));

            byte[] result = new byte[FRAME.length];
            decrypted.get(result);
            assertArrayEquals(FRAME, result);
        }
    }

    @Test
    public void repeatedFramesEncryptDifferently() throws Exception {
        try (Keyed keyed = new Keyed()) {
            byte[] first = keyed.encrypt(FRAME);
            byte[] second = keyed.encrypt(FRAME);
            assertFalse(Arrays.equals(first, second), "nonce was reused");

            byte[] decrypted = new byte[second.length];
            assertEquals(FRAME.length, keyed.decryptor.decrypt(MediaType.AUDIO, second, decrypted));
            assertEquals(FRAME.length, keyed.decryptor.decrypt(MediaType.AUDIO, first, decrypted));
        }
    }

    @Test
    public void replayedFrameIsRejected() throws Exception {
        try (Keyed keyed = new Keyed()) {
            byte[] encrypted = keyed.encrypt(FRAME);
            byte[] decrypted = new byte[encrypted.length];
            assertEquals(FRAME.length, keyed.decryptor.decrypt(MediaType.AUDIO, encrypted, decrypted));

            assertTrue(keyed.decryptor.decrypt(MediaType.AUDIO, encrypted, decrypted) < 0);
        }
    }

    @Test
    public void wrongSenderKeyIsRejected() throws Exception {
        DaveFactory factory = getDaveFactory();
        try (Keyed keyed = new Keyed();
             Decryptor decryptor = factory.createDecryptor()) {
            decryptor.transitionToKeyRatchet(keyed.b.session.getKeyRatchet(USER_B));

            byte[] encrypted = keyed.encrypt(FRAME);
            byte[] decrypted = new byte[encrypted.length];
            Arrays.fill(decrypted, GUARD);
            assertTrue(decryptor.decrypt(MediaType.AUDIO, encrypted, decrypted) < 0);
            for (byte b : decrypted) {
                assertEquals(GUARD, b, "a failed decrypt wrote to the output");
            }
        }
    }

    @Test
    public void tamperedFrameIsRejected() throws Exception {
        try (Keyed keyed = new Keyed()) {
            byte[] encrypted = keyed.encrypt(FRAME);
            encrypted[FRAME.length / 2] ^= 1;

            byte[] decrypted = new byte[encrypted.length];
            assertTrue(keyed.decryptor.decrypt(MediaType.AUDIO, encrypted, decrypted) < 0);
        }
    }

    @Test
    public void truncatedFrameIsRejected() throws Exception {
        try (Keyed keyed = new Keyed()) {
            byte[] encrypted = keyed.encrypt(FRAME);
            byte[] truncated = Arrays.copyOf(encrypted, encrypted.length - 1);

            byte[] decrypted = new byte[encrypted.length];
            assertTrue(keyed.decryptor.decrypt(MediaType.AUDIO, truncated, decrypted) < 0);
        }
    }

    @Test
    public void decryptorWithoutRatchetRejectsEncryptedFrame() throws Exception {
        try (Keyed keyed = new Keyed();
             Decryptor decryptor = getDaveFactory().createDecryptor()) {
            byte[] encrypted = keyed.encrypt(FRAME);
            byte[] decrypted = new byte[encrypted.length];

            assertEquals(-DecryptorResultCode.MISSING_KEY_RATCHET.getValue(),
                    decryptor.decrypt(MediaType.AUDIO, encrypted, decrypted));
        }
    }

    @Test
    public void keyedDecryptorRejectsUnencryptedFrame() throws Exception {
        try (Keyed keyed = new Keyed()) {
            byte[] decrypted = new byte[FRAME.length];
            assertEquals(-DecryptorResultCode.DECRYPTION_FAILURE.getValue(),
                    keyed.decryptor.decrypt(MediaType.AUDIO, FRAME, decrypted));
        }
    }

    @Test
    public void undersizedEncryptOutputIsRejected() throws Exception {
        try (Keyed keyed = new Keyed()) {
            int maxSize = keyed.encryptor.getMaxCiphertextByteSize(MediaType.AUDIO, FRAME.length);
            ByteBuffer frame = TestUtil.arrToDirectByteBuffer(FRAME);

            for (int usable : new int[]{0, FRAME.length / 2, FRAME.length, maxSize - 1}) {
                ByteBuffer encrypted = guarded(usable);
                int result = keyed.encryptor.encrypt(MediaType.AUDIO, SSRC, frame, encrypted);
                assertGuardIntact(encrypted, usable);
                assertEquals(-EncryptorResultCode.ENCRYPTION_FAILURE.getValue(), result, "capacity " + usable);

                byte[] encryptedArray = new byte[usable];
                assertEquals(-EncryptorResultCode.ENCRYPTION_FAILURE.getValue(),
                        keyed.encryptor.encrypt(MediaType.AUDIO, SSRC, FRAME, encryptedArray), "capacity " + usable);
            }

            // The encryptor stays usable after rejecting a buffer.
            keyed.encrypt(FRAME);
        }
    }

    @Test
    public void undersizedDecryptOutputWritesNothing() throws Exception {
        try (Keyed keyed = new Keyed()) {
            ByteBuffer encrypted = TestUtil.arrToDirectByteBuffer(keyed.encrypt(FRAME));
            ByteBuffer decrypted = guarded(FRAME.length - 1);

            // libdave reports success with nothing written when the plaintext does not fit.
            assertEquals(0, keyed.decryptor.decrypt(MediaType.AUDIO, encrypted, decrypted));
            assertGuardIntact(decrypted, FRAME.length - 1);
            for (int i = 0; i < FRAME.length - 1; i++) {
                assertEquals(GUARD, decrypted.get(i));
            }
        }
    }

    @Test
    public void encryptorWithoutRatchetFails() {
        try (Encryptor encryptor = getDaveFactory().createEncryptor()) {
            encryptor.assignSsrcToCodec(SSRC, Codec.OPUS);
            int maxSize = encryptor.getMaxCiphertextByteSize(MediaType.AUDIO, FRAME.length);

            assertEquals(-EncryptorResultCode.MISSING_KEY_RATCHET.getValue(),
                    encryptor.encrypt(MediaType.AUDIO, SSRC, FRAME, new byte[maxSize]));
            assertEquals(-EncryptorResultCode.MISSING_KEY_RATCHET.getValue(),
                    encryptor.encrypt(MediaType.AUDIO, SSRC, TestUtil.arrToDirectByteBuffer(FRAME),
                            ByteBuffer.allocateDirect(maxSize)));
        }
    }

    @Test
    public void ratchetMovesEncryptorOutOfPassthrough() throws Exception {
        try (Keyed keyed = new Keyed()) {
            assertEquals(1, keyed.encryptor.getProtocolVersion());

            keyed.encryptor.setPassthroughMode(true);
            assertEquals(0, keyed.encryptor.getProtocolVersion());
            byte[] out = new byte[FRAME.length];
            assertEquals(FRAME.length, keyed.encryptor.encrypt(MediaType.AUDIO, SSRC, FRAME, out));
            assertArrayEquals(FRAME, out);
        }
    }
}
