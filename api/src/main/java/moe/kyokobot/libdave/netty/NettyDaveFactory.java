package moe.kyokobot.libdave.netty;

import moe.kyokobot.libdave.DaveFactory;
import moe.kyokobot.libdave.Decryptor;
import moe.kyokobot.libdave.Encryptor;

/**
 * Factory for creating Netty-enabled decryptors and encryptors.
 * <p>
 * Creates instances of {@link NettyDecryptor} and {@link NettyEncryptor} that support
 * direct operations on Netty {@code ByteBuf} for improved performance and reduced allocations.
 * <p>
 * <b>Requirements:</b> This requires {@code io.netty:netty-buffer} to be available on the classpath.
 * Loading any classes from this package without Netty will result in {@link ClassNotFoundException}.
 *
 * @see NettyDecryptor
 * @see NettyEncryptor
 */
public interface NettyDaveFactory extends DaveFactory {
    /**
     * Converts a {@link Decryptor} into a Netty-enabled {@link NettyDecryptor}.
     * <p>
     * If the provided decryptor already supports Netty (implements {@link NettyDecryptor}),
     * it is returned unchanged. Otherwise, its internal state is transferred to a new
     * Netty-enabled decryptor, and the original is closed.
     * <p>
     * <b>Note:</b> After calling this method, the original decryptor is closed and should not be used.
     *
     * @param decryptor A decryptor instance created by this factory.
     * @return A Netty-enabled decryptor.
     * @throws IllegalArgumentException if the decryptor was created by an incompatible factory.
     */
    NettyDecryptor fromDecryptor(Decryptor decryptor);

    /**
     * Converts a {@link Encryptor} into a Netty-enabled {@link NettyEncryptor}.
     * <p>
     * If the provided encryptor already supports Netty (implements {@link NettyEncryptor}),
     * it is returned unchanged. Otherwise, its internal state is transferred to a new
     * Netty-enabled encryptor, and the original is closed.
     * <p>
     * <b>Note:</b> After calling this method, the original encryptor is closed and should not be used.
     *
     * @param encryptor An encryptor instance created by this factory.
     * @return A Netty-enabled encryptor.
     * @throws IllegalArgumentException if the encryptor was created by an incompatible factory.
     */
    NettyEncryptor fromEncryptor(Encryptor encryptor);
}
