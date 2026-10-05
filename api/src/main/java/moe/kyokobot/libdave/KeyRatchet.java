package moe.kyokobot.libdave;

/**
 * Represents a key ratchet, which manages the sequence of encryption/decryption keys for a sender.
 * <p>
 * In MLS, keys are "ratcheted" forward to ensure forward secrecy. This class holds the native handle
 * to the ratchet state.
 */
public interface KeyRatchet extends AutoCloseable {
    /**
     * Derives the encryption key for a key generation.
     *
     * @param keyGeneration The key generation.
     * @return The key bytes, or an empty array if the key could not be derived (for example after it was deleted).
     */
    byte[] getEncryptionKey(int keyGeneration);

    /**
     * Deletes the key for a key generation, so it can no longer be derived from this ratchet.
     *
     * @param keyGeneration The key generation.
     */
    void deleteKey(int keyGeneration);

    @Override
    void close();
}
