package moe.kyokobot.libdave.netty;

import io.netty.buffer.ByteBuf;
import moe.kyokobot.libdave.Decryptor;
import moe.kyokobot.libdave.MediaType;

/**
 * A {@link Decryptor} that can also decrypt directly between Netty {@link ByteBuf}s.
 * <p>
 * Both buffers must be direct. The encrypted frame is read from the readable bytes of {@code encryptedFrame}, and the
 * decrypted frame is written at the writer index of {@code frame}, which is advanced on success.
 *
 * @see NettyDaveFactory#fromDecryptor(Decryptor)
 */
public interface NettyDecryptor extends Decryptor {
    /**
     * Decrypts an encrypted frame into Netty ByteBuf.
     *
     * @param mediaType      The type of media.
     * @param encryptedFrame The input ByteBuf containing the encrypted frame.
     * @param frame          The output ByteBuf to write the decrypted frame into.
     * @return The number of bytes written to {@code frame} on success, or a negative error code on failure.
     */
    int decrypt(MediaType mediaType, ByteBuf encryptedFrame, ByteBuf frame);
}
