package moe.kyokobot.libdave.callbacks;

/**
 * Notified when the protocol version used by an {@link moe.kyokobot.libdave.Encryptor} changes.
 *
 * @see moe.kyokobot.libdave.Encryptor#setProtocolVersionChangedCallback(EncryptorProtocolVersionChangedCallback)
 */
public interface EncryptorProtocolVersionChangedCallback {
    /**
     * Called after the encryptor's protocol version has changed, for example when it enters or leaves passthrough
     * mode. The new version can be read with {@link moe.kyokobot.libdave.Encryptor#getProtocolVersion()}.
     */
    void onChanged();
}
