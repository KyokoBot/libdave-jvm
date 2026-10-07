package moe.kyokobot.libdave;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// Ported from libdave test suite
public abstract class CryptorTestBase {
    // @formatter:off
    public static final byte[] RANDOM_BYTES = TestUtil.hexStringToByteArray(
        "0dc5aedd5bdc3f20be5697e54dd1f437b896a36f858c6f20bbd69e2a493ca170c4f0c1b9acd4" +
        "9d324b92afa788d09b12b29115a2feb3552b60fff983234a6c9608af3933683efc6b0f5579a9"
    );
    // @formatter:on

    abstract DaveFactory getDaveFactory();

    @Test
    public void passthroughInOutBuffer() {
        DaveFactory factory = getDaveFactory();

        ByteBuffer frameCopy = TestUtil.arrToDirectByteBuffer(RANDOM_BYTES);

        ByteBuffer frameViewIn = TestUtil.arrToDirectByteBuffer(RANDOM_BYTES);
        ByteBuffer frameViewOut = TestUtil.arrToDirectByteBuffer(RANDOM_BYTES);

        try (Encryptor encryptor = factory.createEncryptor()) {
            encryptor.assignSsrcToCodec(0, Codec.OPUS);
            encryptor.setPassthroughMode(true);

            int encryptResult = encryptor.encrypt(MediaType.AUDIO, 0, frameViewIn, frameViewOut);

            assertTrue(encryptResult >= 0);
            assertEquals(encryptResult, RANDOM_BYTES.length);
            assertEquals(0, frameViewIn.compareTo(frameCopy));
        }

        frameCopy.rewind();
        frameViewIn.rewind();
        frameViewOut.rewind();

        assertEquals(0, frameCopy.position());
        assertTrue(frameCopy.remaining() > 0);

        try (Decryptor decryptor = factory.createDecryptor()) {
            decryptor.transitionToPassthroughMode(true);

            int decryptResult = decryptor.decrypt(MediaType.AUDIO, frameViewIn, frameViewOut);

            assertTrue(decryptResult >= 0);
            assertEquals(decryptResult, RANDOM_BYTES.length);
            assertEquals(0, frameViewIn.compareTo(frameCopy));
        }
    }

    @Test
    public void passthroughTwoBuffers() {
        DaveFactory factory = getDaveFactory();

        ByteBuffer in = TestUtil.arrToDirectByteBuffer(RANDOM_BYTES);
        ByteBuffer encrypted = ByteBuffer.allocateDirect(RANDOM_BYTES.length * 2);
        ByteBuffer decrypted = ByteBuffer.allocateDirect(RANDOM_BYTES.length);

        try (Encryptor encryptor = factory.createEncryptor()) {
            encryptor.assignSsrcToCodec(0, Codec.OPUS);
            encryptor.setPassthroughMode(true);

            in.rewind();
            encrypted.rewind();

            int encryptResult = encryptor.encrypt(MediaType.AUDIO, 0, in, encrypted);

            assertEquals(RANDOM_BYTES.length, encryptResult);
            assertEquals(encryptResult, RANDOM_BYTES.length);

            // encrypted should now contain the original data for the first encryptResult bytes
            encrypted.rewind();
            for (int i = 0; i < encryptResult; i++) {
                assertEquals(RANDOM_BYTES[i], encrypted.get());
            }
        }

        // Now decrypt back
        encrypted.rewind();
        decrypted.rewind();
        try (Decryptor decryptor = factory.createDecryptor()) {
            decryptor.transitionToPassthroughMode(true);

            int decryptResult = decryptor.decrypt(MediaType.AUDIO, encrypted, decrypted);

            assertEquals(RANDOM_BYTES.length, decryptResult);
            assertEquals(decryptResult, RANDOM_BYTES.length);

            decrypted.rewind();
            for (int i = 0; i < decryptResult; i++) {
                assertEquals(RANDOM_BYTES[i], decrypted.get());
            }
        }
    }

    @Test
    public void passthroughByteArrays() {
        DaveFactory factory = getDaveFactory();
        try (Encryptor encryptor = factory.createEncryptor();
             Decryptor decryptor = factory.createDecryptor()) {
            encryptor.assignSsrcToCodec(0, Codec.OPUS);
            encryptor.setPassthroughMode(true);
            decryptor.transitionToPassthroughMode(true);

            byte[] encrypted = new byte[RANDOM_BYTES.length];
            assertEquals(RANDOM_BYTES.length, encryptor.encrypt(MediaType.AUDIO, 0, RANDOM_BYTES, encrypted));
            assertArrayEquals(RANDOM_BYTES, encrypted);

            byte[] decrypted = new byte[RANDOM_BYTES.length];
            assertEquals(RANDOM_BYTES.length, decryptor.decrypt(MediaType.AUDIO, encrypted, decrypted));
            assertArrayEquals(RANDOM_BYTES, decrypted);
        }
    }

    @Test
    public void passthroughTruncatesToOutputCapacity() {
        DaveFactory factory = getDaveFactory();
        int capacity = RANDOM_BYTES.length / 2;
        try (Encryptor encryptor = factory.createEncryptor();
             Decryptor decryptor = factory.createDecryptor()) {
            encryptor.setPassthroughMode(true);
            decryptor.transitionToPassthroughMode(true);

            ByteBuffer encrypted = ByteBuffer.allocateDirect(RANDOM_BYTES.length);
            encrypted.limit(capacity);
            assertEquals(capacity, encryptor.encrypt(MediaType.AUDIO, 0,
                    TestUtil.arrToDirectByteBuffer(RANDOM_BYTES), encrypted));
            encrypted.clear();
            for (int i = capacity; i < RANDOM_BYTES.length; i++) {
                assertEquals(0, encrypted.get(i), "passthrough wrote past the buffer limit");
            }

            byte[] decrypted = new byte[capacity];
            assertEquals(capacity, decryptor.decrypt(MediaType.AUDIO, RANDOM_BYTES, decrypted));
        }
    }

    @Test
    public void emptyFramesPassThrough() {
        DaveFactory factory = getDaveFactory();
        try (Encryptor encryptor = factory.createEncryptor();
             Decryptor decryptor = factory.createDecryptor()) {
            encryptor.setPassthroughMode(true);
            decryptor.transitionToPassthroughMode(true);

            assertEquals(0, encryptor.encrypt(MediaType.AUDIO, 0, new byte[0], new byte[0]));
            assertEquals(0, encryptor.encrypt(MediaType.VIDEO, 0, ByteBuffer.allocateDirect(0),
                    ByteBuffer.allocateDirect(0)));
            assertEquals(0, decryptor.decrypt(MediaType.AUDIO, new byte[0], new byte[0]));
        }
    }

    @Test
    public void decryptorRejectsUnencryptedFrameWithoutPassthrough() {
        try (Decryptor decryptor = getDaveFactory().createDecryptor()) {
            assertEquals(-DecryptorResultCode.DECRYPTION_FAILURE.getValue(),
                    decryptor.decrypt(MediaType.AUDIO, RANDOM_BYTES, new byte[RANDOM_BYTES.length]));
        }
    }

    @Test
    public void heapByteBuffersAreRejected() {
        DaveFactory factory = getDaveFactory();
        ByteBuffer direct = ByteBuffer.allocateDirect(RANDOM_BYTES.length);
        ByteBuffer heap = ByteBuffer.allocate(RANDOM_BYTES.length);
        try (Encryptor encryptor = factory.createEncryptor();
             Decryptor decryptor = factory.createDecryptor()) {
            encryptor.setPassthroughMode(true);
            decryptor.transitionToPassthroughMode(true);

            assertThrows(IllegalArgumentException.class, () -> encryptor.encrypt(MediaType.AUDIO, 0, heap, direct));
            assertThrows(IllegalArgumentException.class, () -> encryptor.encrypt(MediaType.AUDIO, 0, direct, heap));
            assertThrows(IllegalArgumentException.class, () -> decryptor.decrypt(MediaType.AUDIO, heap, direct));
            assertThrows(IllegalArgumentException.class, () -> decryptor.decrypt(MediaType.AUDIO, direct, heap));
        }
    }

    @Test
    public void nullArgumentsAreRejected() {
        DaveFactory factory = getDaveFactory();
        byte[] array = new byte[1];
        ByteBuffer buffer = ByteBuffer.allocateDirect(1);
        try (Encryptor encryptor = factory.createEncryptor();
             Decryptor decryptor = factory.createDecryptor()) {
            encryptor.setPassthroughMode(true);
            decryptor.transitionToPassthroughMode(true);

            assertThrows(NullPointerException.class, () -> encryptor.encrypt(MediaType.AUDIO, 0, null, array));
            assertThrows(NullPointerException.class, () -> encryptor.encrypt(MediaType.AUDIO, 0, array, null));
            assertThrows(NullPointerException.class,
                    () -> encryptor.encrypt(MediaType.AUDIO, 0, (ByteBuffer) null, buffer));
            assertThrows(NullPointerException.class,
                    () -> encryptor.encrypt(MediaType.AUDIO, 0, buffer, (ByteBuffer) null));
            assertThrows(NullPointerException.class, () -> encryptor.encrypt(null, 0, array, array));
            assertThrows(NullPointerException.class, () -> encryptor.assignSsrcToCodec(0, null));
            assertThrows(NullPointerException.class, () -> decryptor.decrypt(MediaType.AUDIO, null, array));
            assertThrows(NullPointerException.class, () -> decryptor.decrypt(MediaType.AUDIO, array, null));
            assertThrows(NullPointerException.class,
                    () -> decryptor.decrypt(MediaType.AUDIO, (ByteBuffer) null, buffer));
            assertThrows(NullPointerException.class,
                    () -> decryptor.decrypt(MediaType.AUDIO, buffer, (ByteBuffer) null));
            assertThrows(NullPointerException.class, () -> decryptor.decrypt(null, array, array));

            assertEquals(1, encryptor.encrypt(MediaType.AUDIO, 0, array, array));
        }
    }

    @Test
    public void closedCryptorsRejectCalls() {
        DaveFactory factory = getDaveFactory();
        byte[] array = new byte[1];
        ByteBuffer buffer = ByteBuffer.allocateDirect(1);

        Encryptor encryptor = factory.createEncryptor();
        encryptor.close();
        encryptor.close();
        assertThrows(IllegalStateException.class, () -> encryptor.setPassthroughMode(true));
        assertThrows(IllegalStateException.class, () -> encryptor.assignSsrcToCodec(0, Codec.OPUS));
        assertThrows(IllegalStateException.class, encryptor::getProtocolVersion);
        assertThrows(IllegalStateException.class, () -> encryptor.encrypt(MediaType.AUDIO, 0, array, array));
        assertThrows(IllegalStateException.class, () -> encryptor.encrypt(MediaType.AUDIO, 0, buffer, buffer));
        assertThrows(IllegalStateException.class, () -> encryptor.setProtocolVersionChangedCallback(() -> {
        }));

        Decryptor decryptor = factory.createDecryptor();
        decryptor.close();
        decryptor.close();
        assertThrows(IllegalStateException.class, () -> decryptor.transitionToPassthroughMode(true));
        assertThrows(IllegalStateException.class, () -> decryptor.decrypt(MediaType.AUDIO, array, array));
        assertThrows(IllegalStateException.class, () -> decryptor.decrypt(MediaType.AUDIO, buffer, buffer));
    }

    @Test
    public void throwingProtocolVersionCallbackDoesNotPropagate() {
        AtomicInteger calls = new AtomicInteger();
        try (Encryptor encryptor = getDaveFactory().createEncryptor()) {
            encryptor.setProtocolVersionChangedCallback(() -> {
                calls.incrementAndGet();
                throw new IllegalStateException("thrown from the protocol version callback on purpose");
            });

            encryptor.setPassthroughMode(true);
            encryptor.setPassthroughMode(false);
            assertEquals(2, calls.get());

            // The encryptor keeps working after the callback threw.
            encryptor.setPassthroughMode(true);
            assertEquals(1, encryptor.encrypt(MediaType.AUDIO, 0, new byte[]{42}, new byte[1]));
        }
    }

    @Test
    public void replacedProtocolVersionCallbackIsNotCalled() {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        try (Encryptor encryptor = getDaveFactory().createEncryptor()) {
            encryptor.setProtocolVersionChangedCallback(first::incrementAndGet);
            encryptor.setProtocolVersionChangedCallback(second::incrementAndGet);

            encryptor.setPassthroughMode(true);
            assertEquals(0, first.get());
            assertEquals(1, second.get());
        }
    }
}
