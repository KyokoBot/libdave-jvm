package moe.kyokobot.libdave;

import moe.kyokobot.libdave.callbacks.MLSFailureCallback;

/**
 * Entry point for creating DAVE sessions, encryptors and decryptors.
 * <p>
 * Objects created by one factory implementation can only be combined with objects from the same implementation.
 */
public interface DaveFactory {
    /**
     * Returns the maximum protocol version supported by the underlying native library.
     *
     * @return The maximum supported DAVE protocol version.
     */
    int maxSupportedProtocolVersion();

    /**
     * Creates a new Decryptor instance.
     *
     * @return A new decryptor, which must be closed when no longer needed.
     */
    Decryptor createDecryptor();

    /**
     * Creates a new Encryptor instance.
     *
     * @return A new encryptor, which must be closed when no longer needed.
     */
    Encryptor createEncryptor();

    /**
     * Creates a new DAVE session.
     *
     * @param context       A string context for the session, often used for logging or identifying the session.
     * @param authSessionId The authentication session ID associated with the user.
     * @param callback      Callback to handle MLS failures, such as invalid transitions.
     * @return A new session, which must be closed when no longer needed.
     */
    Session createSession(String context, String authSessionId, MLSFailureCallback callback);
}
