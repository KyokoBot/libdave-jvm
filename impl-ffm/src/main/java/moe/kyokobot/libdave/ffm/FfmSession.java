package moe.kyokobot.libdave.ffm;

import moe.kyokobot.libdave.CommitResult;
import moe.kyokobot.libdave.KeyRatchet;
import moe.kyokobot.libdave.RosterMap;
import moe.kyokobot.libdave.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static moe.kyokobot.libdave.impl.Constants.COMMIT_RESULT_FAILED;
import static moe.kyokobot.libdave.impl.Constants.COMMIT_RESULT_IGNORED;

public class FfmSession extends FfmHandle implements Session {
    public FfmSession(MemorySegment handle) {
        super(handle);
    }

    private static MemorySegment allocateStrings(Arena arena, String[] strings) {
        MemorySegment array = arena.allocate(ADDRESS, strings.length);
        for (int i = 0; i < strings.length; i++) {
            MemorySegment string = strings[i] != null ? arena.allocateFrom(strings[i]) : MemorySegment.NULL;
            array.setAtIndex(ADDRESS, i, string);
        }
        return array;
    }

    private static RosterMap takeRoster(MemorySegment roster) {
        try {
            int size = Math.toIntExact(KyokoDave.rosterSize(roster));
            long[] keys = new long[size];
            byte[][] values = new byte[size][];
            for (int i = 0; i < size; i++) {
                keys[i] = KyokoDave.rosterGetUserId(roster, i);
                long keySize = KyokoDave.rosterGetKeySize(roster, i);
                values[i] = KyokoDave.rosterGetKeyData(roster, i).reinterpret(keySize).toArray(JAVA_BYTE);
            }
            return new RosterMap(keys, values);
        } finally {
            KyokoDave.rosterDestroy(roster);
        }
    }

    @Override
    public void init(int version, long groupId, String selfUserId) {
        assertOpen();
        Objects.requireNonNull(selfUserId, "selfUserId");
        try (Arena arena = Arena.ofConfined()) {
            KyokoDave.sessionInit(handle, version, groupId, arena.allocateFrom(selfUserId));
        }
    }

    @Override
    public void reset() {
        assertOpen();
        KyokoDave.sessionReset(handle);
    }

    @Override
    public void setProtocolVersion(int version) {
        assertOpen();
        KyokoDave.sessionSetProtocolVersion(handle, version);
    }

    @Override
    public int getProtocolVersion() {
        assertOpen();
        return KyokoDave.sessionGetProtocolVersion(handle);
    }

    @Override
    public byte[] getLastEpochAuthenticator() {
        assertOpen();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outData = arena.allocate(ADDRESS);
            MemorySegment outSize = KyokoDave.allocateSize(arena);
            KyokoDave.sessionGetLastEpochAuthenticator(handle, outData, outSize);
            return takeBytes(outData, outSize);
        }
    }

    @Override
    public void setExternalSender(byte[] externalSender) {
        assertOpen();
        Objects.requireNonNull(externalSender, "externalSender");
        try (Arena arena = Arena.ofConfined()) {
            KyokoDave.sessionSetExternalSender(handle, allocateBytes(arena, externalSender), externalSender.length);
        }
    }

    @Override
    public byte[] processProposals(byte @NotNull [] proposals, @NotNull String[] recognizedUserIds) {
        assertOpen();
        Objects.requireNonNull(proposals, "proposals");
        Objects.requireNonNull(recognizedUserIds, "recognizedUserIds");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outData = arena.allocate(ADDRESS);
            MemorySegment outSize = KyokoDave.allocateSize(arena);
            boolean hasResult = KyokoDave.sessionProcessProposals(handle,
                    allocateBytes(arena, proposals), proposals.length,
                    allocateStrings(arena, recognizedUserIds), recognizedUserIds.length,
                    outData, outSize);
            return hasResult ? takeBytes(outData, outSize) : null;
        }
    }

    @Override
    public @NotNull CommitResult processCommit(byte @NotNull [] commit) {
        assertOpen();
        Objects.requireNonNull(commit, "commit");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outRoster = arena.allocate(ADDRESS);
            int result = KyokoDave.sessionProcessCommit(handle, allocateBytes(arena, commit), commit.length,
                    outRoster);
            if (result == COMMIT_RESULT_FAILED) return CommitResult.failed();
            if (result == COMMIT_RESULT_IGNORED) return CommitResult.ignored();
            return CommitResult.success(takeRoster(outRoster.get(ADDRESS, 0)));
        }
    }

    @Override
    public @Nullable RosterMap processWelcome(byte @NotNull [] welcome, @NotNull String[] recognizedUserIds) {
        assertOpen();
        Objects.requireNonNull(welcome, "welcome");
        Objects.requireNonNull(recognizedUserIds, "recognizedUserIds");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment roster = KyokoDave.sessionProcessWelcome(handle,
                    allocateBytes(arena, welcome), welcome.length,
                    allocateStrings(arena, recognizedUserIds), recognizedUserIds.length);
            if (roster.address() == 0) return null;
            return takeRoster(roster);
        }
    }

    @Override
    public byte[] getMarshalledKeyPackage() {
        assertOpen();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outData = arena.allocate(ADDRESS);
            MemorySegment outSize = KyokoDave.allocateSize(arena);
            KyokoDave.sessionGetMarshalledKeyPackage(handle, outData, outSize);
            return takeBytes(outData, outSize);
        }
    }

    @Override
    public @Nullable KeyRatchet getKeyRatchet(String userId) {
        assertOpen();
        Objects.requireNonNull(userId, "userId");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment keyRatchet = KyokoDave.sessionGetKeyRatchet(handle, arena.allocateFrom(userId));
            if (keyRatchet.address() == 0) return null;
            return new FfmKeyRatchet(keyRatchet);
        }
    }

    @Override
    public CompletableFuture<byte[]> getPairwiseFingerprint(int version, String userId) {
        assertOpen();
        Objects.requireNonNull(userId, "userId");
        CompletableFuture<byte[]> future = new CompletableFuture<>();

        MemorySegment userData = Callbacks.register((Consumer<byte[]>) future::complete);
        try (Arena arena = Arena.ofConfined()) {
            KyokoDave.sessionGetPairwiseFingerprint(handle, version, arena.allocateFrom(userId),
                    Callbacks.PAIRWISE_FINGERPRINT, userData, Callbacks.USER_DATA_FREE);
        }

        return future;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        KyokoDave.sessionDestroy(handle);
    }
}
