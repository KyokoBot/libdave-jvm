package moe.kyokobot.libdave.impl;

import moe.kyokobot.libdave.CommitResult;
import moe.kyokobot.libdave.KeyRatchet;
import moe.kyokobot.libdave.RosterMap;
import moe.kyokobot.libdave.Session;
import moe.kyokobot.libdave.natives.DaveNativeBindings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static moe.kyokobot.libdave.impl.Constants.COMMIT_RESULT_FAILED;
import static moe.kyokobot.libdave.impl.Constants.COMMIT_RESULT_IGNORED;

public class NativeSession extends DaveNativeHandle implements Session {
    public NativeSession(long handle) {
        super(handle);
    }

    @Override
    public void init(int version, long groupId, String selfUserId) {
        assertOpen();
        Objects.requireNonNull(selfUserId, "selfUserId");
        DaveNativeBindings.inst().daveSessionInit(handle, version, groupId, selfUserId);
    }

    @Override
    public void reset() {
        assertOpen();
        DaveNativeBindings.inst().daveSessionReset(handle);
    }

    @Override
    public void setProtocolVersion(int version) {
        assertOpen();
        DaveNativeBindings.inst().daveSessionSetProtocolVersion(handle, version);
    }

    @Override
    public int getProtocolVersion() {
        assertOpen();
        return DaveNativeBindings.inst().daveSessionGetProtocolVersion(handle);
    }

    @Override
    public byte[] getLastEpochAuthenticator() {
        assertOpen();
        return DaveNativeBindings.inst().daveSessionGetLastEpochAuthenticator(handle);
    }

    @Override
    public void setExternalSender(byte[] externalSender) {
        assertOpen();
        Objects.requireNonNull(externalSender, "externalSender");
        DaveNativeBindings.inst().daveSessionSetExternalSender(handle, externalSender);
    }

    @Override
    public byte[] processProposals(byte @NotNull [] proposals, @NotNull String[] recognizedUserIds) {
        assertOpen();
        Objects.requireNonNull(proposals, "proposals");
        Objects.requireNonNull(recognizedUserIds, "recognizedUserIds");

        return DaveNativeBindings.inst().daveSessionProcessProposals(handle, proposals, recognizedUserIds);
    }

    @Override
    public @NotNull CommitResult processCommit(byte @NotNull [] commit) {
        assertOpen();
        Objects.requireNonNull(commit, "commit");

        Object result = DaveNativeBindings.inst().daveSessionProcessCommit(handle, commit);
        if (result instanceof Integer) {
            int r = (Integer) result;
            if (r == COMMIT_RESULT_FAILED) return CommitResult.failed();
            if (r == COMMIT_RESULT_IGNORED) return CommitResult.ignored();
        } else if (result instanceof RosterMap) {
            return CommitResult.success((RosterMap) result);
        }

        throw new IllegalStateException("Unexpected result type from JNI: " + result);
    }

    @Override
    public @Nullable RosterMap processWelcome(byte @NotNull [] welcome, @NotNull String[] recognizedUserIds) {
        assertOpen();
        Objects.requireNonNull(welcome, "welcome");
        Objects.requireNonNull(recognizedUserIds, "recognizedUserIds");
        return DaveNativeBindings.inst().daveSessionProcessWelcome(handle, welcome, recognizedUserIds);
    }

    @Override
    public byte[] getMarshalledKeyPackage() {
        assertOpen();
        return DaveNativeBindings.inst().daveSessionGetMarshalledKeyPackage(handle);
    }

    @Override
    public @Nullable KeyRatchet getKeyRatchet(String userId) {
        assertOpen();
        Objects.requireNonNull(userId, "userId");
        long keyRatchetHandle = DaveNativeBindings.inst().daveSessionGetKeyRatchet(handle, userId);
        if (keyRatchetHandle == 0) return null;
        return new NativeKeyRatchet(keyRatchetHandle);
    }

    @Override
    public CompletableFuture<byte[]> getPairwiseFingerprint(int version, String userId) {
        assertOpen();
        Objects.requireNonNull(userId, "userId");
        CompletableFuture<byte[]> future = new CompletableFuture<>();

        DaveNativeBindings.inst().daveSessionGetPairwiseFingerprint(handle, version, userId, future::complete);

        return future;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        DaveNativeBindings.inst().daveSessionDestroy(handle);
    }
}
