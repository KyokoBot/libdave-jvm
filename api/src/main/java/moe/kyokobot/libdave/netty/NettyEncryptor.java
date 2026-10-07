package moe.kyokobot.libdave.netty;

import io.netty.buffer.ByteBuf;
import moe.kyokobot.libdave.Encryptor;
import moe.kyokobot.libdave.MediaType;

/**
 * An {@link Encryptor} that can also encrypt directly between Netty {@link ByteBuf}s.
 * <p>
 * Both buffers must be direct. The frame is read from the readable bytes of {@code frame}, and the encrypted frame is
 * written at the writer index of {@code encryptedFrame}, which is advanced on success.
 *
 * @see NettyDaveFactory#fromEncryptor(Encryptor)
 */
public interface NettyEncryptor extends Encryptor {
    /**
     * Encrypts a media frame into Netty ByteBuf.
     *
     * @param mediaType      The type of media.
     * @param ssrc           The SSRC of the stream.
     * @param frame          The input ByteBuf containing the plaintext frame.
     * @param encryptedFrame The output ByteBuf to write the encrypted frame into. Outside passthrough mode it
     *                       must have room for {@link #getMaxCiphertextByteSize} bytes, or encryption fails.
     * @return The number of bytes written to {@code encryptedFrame} on success, or a negative error code on failure.
     */
    int encrypt(MediaType mediaType, int ssrc, ByteBuf frame, ByteBuf encryptedFrame);
}
