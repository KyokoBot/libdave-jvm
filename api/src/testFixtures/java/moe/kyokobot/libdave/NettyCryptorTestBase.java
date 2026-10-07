package moe.kyokobot.libdave;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.UnpooledDirectByteBuf;
import moe.kyokobot.libdave.TestGroup.Member;
import moe.kyokobot.libdave.netty.NettyDaveFactory;
import moe.kyokobot.libdave.netty.NettyDecryptor;
import moe.kyokobot.libdave.netty.NettyEncryptor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.function.IntFunction;

import static moe.kyokobot.libdave.TestGroup.USER_A;
import static org.junit.jupiter.api.Assertions.*;

public abstract class NettyCryptorTestBase {
    private static final byte[] FRAME = CryptorTestBase.RANDOM_BYTES;
    private static final int SSRC = 1234;

    abstract NettyDaveFactory getDaveFactory();

    // Direct buffers with a memory address take the native pointer fast path.
    private static final IntFunction<ByteBuf> ADDRESSABLE = Unpooled::directBuffer;
    // Direct buffers without one fall back to NIO buffers.
    private static final IntFunction<ByteBuf> NIO_ONLY =
            capacity -> new UnpooledDirectByteBuf(UnpooledByteBufAllocator.DEFAULT, capacity, capacity);

    @Test
    public void passthroughDirectByteBuf() {
        NettyDaveFactory factory = getDaveFactory();
        byte[] data = CryptorTestBase.RANDOM_BYTES;

        ByteBuf in = TestUtil.arrToDirectByteBuf(data);
        ByteBuf encrypted = Unpooled.directBuffer(data.length * 2);
        ByteBuf decrypted = Unpooled.directBuffer(data.length);
        try (NettyEncryptor encryptor = factory.fromEncryptor(factory.createEncryptor());
             NettyDecryptor decryptor = factory.fromDecryptor(factory.createDecryptor())) {
            encryptor.assignSsrcToCodec(0, Codec.OPUS);
            encryptor.setPassthroughMode(true);
            decryptor.transitionToPassthroughMode(true);

            assertEquals(data.length, encryptor.encrypt(MediaType.AUDIO, 0, in, encrypted));
            assertEquals(data.length, encrypted.readableBytes());

            assertEquals(data.length, decryptor.decrypt(MediaType.AUDIO, encrypted, decrypted));
            assertEquals(Unpooled.wrappedBuffer(data), decrypted);
        } finally {
            in.release();
            encrypted.release();
            decrypted.release();
        }
    }

    @Test
    public void addressableBuffersHonourIndices() {
        assertIndicesHonoured(ADDRESSABLE);
    }

    @Test
    public void nioOnlyBuffersHonourIndices() {
        assertIndicesHonoured(NIO_ONLY);
    }

    private void assertIndicesHonoured(IntFunction<ByteBuf> allocator) {
        NettyDaveFactory factory = getDaveFactory();
        ByteBuf in = allocator.apply(FRAME.length + 2);
        ByteBuf out = allocator.apply(FRAME.length + 3);
        try (NettyEncryptor encryptor = factory.fromEncryptor(factory.createEncryptor())) {
            encryptor.setPassthroughMode(true);
            in.writeBytes(new byte[]{9, 9}).writeBytes(FRAME).readerIndex(2);
            out.writeBytes(new byte[]{7, 7, 7});

            assertEquals(FRAME.length, encryptor.encrypt(MediaType.AUDIO, SSRC, in, out));
            assertEquals(2, in.readerIndex(), "the input reader index must not move");
            assertEquals(3 + FRAME.length, out.writerIndex());
            assertEquals(7, out.getByte(2));
            byte[] written = new byte[FRAME.length];
            out.getBytes(3, written);
            assertArrayEquals(FRAME, written);
        } finally {
            in.release();
            out.release();
        }
    }

    @Test
    public void addressableBuffersRoundTripWithMlsKeys() throws Exception {
        assertMlsRoundTrip(ADDRESSABLE);
    }

    @Test
    public void nioOnlyBuffersRoundTripWithMlsKeys() throws Exception {
        assertMlsRoundTrip(NIO_ONLY);
    }

    private void assertMlsRoundTrip(IntFunction<ByteBuf> allocator) throws Exception {
        NettyDaveFactory factory = getDaveFactory();
        try (TestGroup group = new TestGroup(factory);
             NettyEncryptor encryptor = factory.fromEncryptor(factory.createEncryptor());
             NettyDecryptor decryptor = factory.fromDecryptor(factory.createDecryptor())) {
            Member[] members = group.establish();
            encryptor.assignSsrcToCodec(SSRC, Codec.OPUS);
            encryptor.setKeyRatchet(members[0].session.getKeyRatchet(USER_A));
            decryptor.transitionToKeyRatchet(members[1].session.getKeyRatchet(USER_A));

            int maxSize = encryptor.getMaxCiphertextByteSize(MediaType.AUDIO, FRAME.length);
            ByteBuf in = allocator.apply(FRAME.length);
            ByteBuf encrypted = allocator.apply(maxSize);
            ByteBuf decrypted = allocator.apply(maxSize);
            try {
                in.writeBytes(FRAME);
                int written = encryptor.encrypt(MediaType.AUDIO, SSRC, in, encrypted);
                assertTrue(written > FRAME.length, "encrypt failed: " + written);
                assertEquals(written, encrypted.readableBytes());

                assertEquals(FRAME.length, decryptor.decrypt(MediaType.AUDIO, encrypted, decrypted));
                assertEquals(Unpooled.wrappedBuffer(FRAME), decrypted);
            } finally {
                in.release();
                encrypted.release();
                decrypted.release();
            }
        }
    }

    @Test
    public void failureDoesNotAdvanceWriterIndex() {
        NettyDaveFactory factory = getDaveFactory();
        ByteBuf in = TestUtil.arrToDirectByteBuf(FRAME);
        ByteBuf out = Unpooled.directBuffer(FRAME.length * 2);
        try (NettyEncryptor encryptor = factory.fromEncryptor(factory.createEncryptor());
             NettyDecryptor decryptor = factory.fromDecryptor(factory.createDecryptor())) {
            encryptor.assignSsrcToCodec(SSRC, Codec.OPUS);

            assertTrue(encryptor.encrypt(MediaType.AUDIO, SSRC, in, out) < 0);
            assertEquals(0, out.writerIndex());
            assertTrue(decryptor.decrypt(MediaType.AUDIO, in, out) < 0);
            assertEquals(0, out.writerIndex());
        } finally {
            in.release();
            out.release();
        }
    }

    @Test
    public void heapByteBufsAreRejected() {
        NettyDaveFactory factory = getDaveFactory();
        ByteBuf direct = TestUtil.arrToDirectByteBuf(FRAME);
        ByteBuf heap = Unpooled.buffer(FRAME.length * 2).writeBytes(FRAME);
        try (NettyEncryptor encryptor = factory.fromEncryptor(factory.createEncryptor());
             NettyDecryptor decryptor = factory.fromDecryptor(factory.createDecryptor())) {
            encryptor.setPassthroughMode(true);
            decryptor.transitionToPassthroughMode(true);

            assertThrows(IllegalArgumentException.class, () -> encryptor.encrypt(MediaType.AUDIO, 0, heap, direct));
            assertThrows(IllegalArgumentException.class, () -> encryptor.encrypt(MediaType.AUDIO, 0, direct, heap));
            assertThrows(IllegalArgumentException.class, () -> decryptor.decrypt(MediaType.AUDIO, heap, direct));
            assertThrows(IllegalArgumentException.class, () -> decryptor.decrypt(MediaType.AUDIO, direct, heap));
        } finally {
            direct.release();
            heap.release();
        }
    }

    @Test
    public void conversionTakesOverTheOriginal() {
        NettyDaveFactory factory = getDaveFactory();
        Encryptor encryptor = factory.createEncryptor();
        Decryptor decryptor = factory.createDecryptor();
        try (NettyEncryptor nettyEncryptor = factory.fromEncryptor(encryptor);
             NettyDecryptor nettyDecryptor = factory.fromDecryptor(decryptor)) {
            assertThrows(IllegalStateException.class, encryptor::getProtocolVersion);
            assertThrows(IllegalStateException.class, () -> decryptor.transitionToPassthroughMode(true));
            // Closing the original must not free the native object the Netty wrapper now owns.
            encryptor.close();
            decryptor.close();

            assertSame(nettyEncryptor, factory.fromEncryptor(nettyEncryptor));
            assertSame(nettyDecryptor, factory.fromDecryptor(nettyDecryptor));
            assertThrows(IllegalStateException.class, () -> factory.fromEncryptor(encryptor));
            assertThrows(IllegalStateException.class, () -> factory.fromDecryptor(decryptor));

            nettyEncryptor.setPassthroughMode(true);
            assertEquals(0, nettyEncryptor.getProtocolVersion());
            nettyDecryptor.transitionToPassthroughMode(true);
        }
    }

    @Test
    public void foreignCryptorsAreRejected() {
        NettyDaveFactory factory = getDaveFactory();
        Encryptor encryptor = foreign(Encryptor.class);
        Decryptor decryptor = foreign(Decryptor.class);

        assertThrows(IllegalArgumentException.class, () -> factory.fromEncryptor(encryptor));
        assertThrows(IllegalArgumentException.class, () -> factory.fromDecryptor(decryptor));
    }

    private static <T> T foreign(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    throw new AssertionError("foreign object must not be used: " + method);
                }));
    }

    @Test
    public void closedNettyCryptorsRejectCalls() {
        NettyDaveFactory factory = getDaveFactory();
        ByteBuf buf = TestUtil.arrToDirectByteBuf(FRAME);
        try {
            NettyEncryptor encryptor = factory.fromEncryptor(factory.createEncryptor());
            encryptor.close();
            assertThrows(IllegalStateException.class, () -> encryptor.encrypt(MediaType.AUDIO, 0, buf, buf));

            NettyDecryptor decryptor = factory.fromDecryptor(factory.createDecryptor());
            decryptor.close();
            assertThrows(IllegalStateException.class, () -> decryptor.decrypt(MediaType.AUDIO, buf, buf));
        } finally {
            buf.release();
        }
    }
}
