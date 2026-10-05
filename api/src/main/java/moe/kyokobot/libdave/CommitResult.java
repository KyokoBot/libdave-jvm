package moe.kyokobot.libdave;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static moe.kyokobot.libdave.impl.Constants.COMMIT_RESULT_FAILED;
import static moe.kyokobot.libdave.impl.Constants.COMMIT_RESULT_IGNORED;

/**
 * Represents the result of processing an MLS commit via {@link Session#processCommit(byte[])}.
 * <p>
 * This class encapsulates the status of the operation (success, failure, ignored) and, in case of success,
 * the changes to the group roster (added, updated, or removed keys).
 */
public class CommitResult {
    private static final CommitResult INSTANCE_FAILED = new CommitResult(COMMIT_RESULT_FAILED);
    private static final CommitResult INSTANCE_IGNORED = new CommitResult(COMMIT_RESULT_IGNORED);

    /**
     * Returns the result for a commit that failed to process.
     *
     * @return The shared failed result.
     */
    public static CommitResult failed() {
        return INSTANCE_FAILED;
    }

    /**
     * Returns the result for a commit that was ignored.
     *
     * @return The shared ignored result.
     */
    public static CommitResult ignored() {
        return INSTANCE_IGNORED;
    }

    /**
     * Creates the result for a successfully processed commit.
     *
     * @param rosterMap The roster changes resulting from the commit.
     * @return A successful result carrying {@code rosterMap}.
     */
    public static CommitResult success(@NotNull RosterMap rosterMap) {
        return new CommitResult(rosterMap);
    }

    private final @Nullable RosterMap rosterMap;
    private final int resultCode;

    private CommitResult(@NotNull RosterMap rosterMap) {
        this.rosterMap = Objects.requireNonNull(rosterMap);
        this.resultCode = 0;
    }

    private CommitResult(int resultCode) {
        this.rosterMap = null;
        this.resultCode = resultCode;
    }

    /**
     * Checks if the commit processing failed.
     *
     * @return {@code true} if the operation failed, {@code false} otherwise.
     */
    public boolean isFailed() {
        return resultCode == COMMIT_RESULT_FAILED;
    }

    /**
     * Checks if the commit was ignored (e.g., duplicate or irrelevant).
     *
     * @return {@code true} if the commit was ignored, {@code false} otherwise.
     */
    public boolean isIgnored() {
        return resultCode == COMMIT_RESULT_IGNORED;
    }

    /**
     * Retrieves the roster map detailing key changes resulting from the commit.
     * <p>
     * The map keys are user IDs. The values are the new key data, or null/empty if the user's key was removed.
     *
     * @return The {@link RosterMap} with the changes.
     * @throws IllegalStateException if the result is failed or ignored.
     */
    @NotNull
    public RosterMap getRosterMap() {
        if (rosterMap == null) {
            throw new IllegalStateException("Cannot get roster map from failed/ignored result");
        }
        return rosterMap;
    }
}

