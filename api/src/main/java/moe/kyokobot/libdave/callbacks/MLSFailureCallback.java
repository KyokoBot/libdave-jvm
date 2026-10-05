package moe.kyokobot.libdave.callbacks;

/**
 * Notified when a {@link moe.kyokobot.libdave.Session} fails to process an MLS message or operation.
 *
 * @see moe.kyokobot.libdave.DaveFactory#createSession(String, String, MLSFailureCallback)
 */
public interface MLSFailureCallback {
    /**
     * Called when an MLS operation fails.
     *
     * @param source The name of the operation that failed.
     * @param reason A human-readable description of the failure.
     */
    void onFailure(String source, String reason);
}
