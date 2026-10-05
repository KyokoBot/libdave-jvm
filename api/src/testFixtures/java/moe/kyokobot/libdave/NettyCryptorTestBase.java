package moe.kyokobot.libdave;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import moe.kyokobot.libdave.netty.NettyDaveFactory;
import moe.kyokobot.libdave.netty.NettyDecryptor;
import moe.kyokobot.libdave.netty.NettyEncryptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class NettyCryptorTestBase {
    abstract NettyDaveFactory getDaveFactory();

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
}
