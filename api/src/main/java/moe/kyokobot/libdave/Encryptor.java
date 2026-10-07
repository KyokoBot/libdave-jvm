package moe.kyokobot.libdave;

import moe.kyokobot.libdave.callbacks.EncryptorProtocolVersionChangedCallback;

import java.nio.ByteBuffer;

/**
 * Handles the encryption of outbound media frames using DAVE.
 * <p>
 * The Encryptor uses the local user's {@link KeyRatchet} to encrypt frames before they are sent.
 */
public interface Encryptor extends AutoCloseable {
    /**
     * Sets the key ratchet to be used for encryption.
     * <p>
     * This typically corresponds to the local user's ratchet derived from the session.
     *
     * @param keyRatchet The key ratchet.
     */
    void setKeyRatchet(KeyRatchet keyRatchet);

    /**
     * Sets whether the encryptor should operate in passthrough mode.
     *
     * @param passthroughMode {@code true} to enable passthrough, {@code false} to disable.
     */
    void setPassthroughMode(boolean passthroughMode);

    /**
     * Assigns an SSRC (Synchronization Source identifier) to a specific codec.
     * <p>
     * This helps the encryptor understand how to parse and encrypt frames for a given stream.
     *
     * @param ssrc  The SSRC identifier.
     * @param codec The codec used for this SSRC.
     */
    void assignSsrcToCodec(int ssrc, Codec codec);

    /**
     * Gets the current protocol version used by the encryptor.
     *
     * @return The protocol version.
     */
    int getProtocolVersion();

    /**
     * Calculates the maximum possible size of the ciphertext (encrypted) frame for a given plaintext frame size.
     *
     * @param mediaType The type of media.
     * @param frameSize The size of the plaintext frame.
     * @return The maximum size of the encrypted frame in bytes.
     */
    default int getMaxCiphertextByteSize(MediaType mediaType, int frameSize) {
        final int kSupplementalBytes = 11;
        final int kTransformPaddingBytes = 64;
        return frameSize + kSupplementalBytes + kTransformPaddingBytes;
    }

    /**
     * Encrypts a media frame.
     *
     * @param mediaType      The type of media.
     * @param ssrc           The SSRC of the stream.
     * @param frame          The input buffer containing the plaintext frame.
     * @param encryptedFrame The output buffer to write the encrypted frame into. Outside passthrough mode it
     *                       must have room for {@link #getMaxCiphertextByteSize} bytes, or encryption fails.
     * @return The number of bytes written to {@code encryptedFrame} on success, or a negative error code (see {@link EncryptorResultCode}) on failure.
     */
    int encrypt(MediaType mediaType, int ssrc, byte[] frame, byte[] encryptedFrame);

    /**
     * Encrypts a media frame into NIO ByteBuffer.
     *
     * @param mediaType      The type of media.
     * @param ssrc           The SSRC of the stream.
     * @param frame          The input ByteBuffer containing the plaintext frame.
     * @param encryptedFrame The output ByteBuffer to write the encrypted frame into. Outside passthrough mode it
     *                       must have room for {@link #getMaxCiphertextByteSize} bytes, or encryption fails.
     * @return The number of bytes written to {@code encryptedFrame} on success, or a negative error code on failure.
     */
    int encrypt(MediaType mediaType, int ssrc, ByteBuffer frame, ByteBuffer encryptedFrame);

    /**
     * Sets a callback to be notified when the protocol version changes.
     *
     * @param callback The callback listener.
     */
    void setProtocolVersionChangedCallback(EncryptorProtocolVersionChangedCallback callback);

    @Override
    void close();
}
