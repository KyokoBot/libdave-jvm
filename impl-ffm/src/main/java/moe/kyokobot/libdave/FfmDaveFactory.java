package moe.kyokobot.libdave;

import moe.kyokobot.libdave.callbacks.DaveLogSink;
import moe.kyokobot.libdave.callbacks.MLSFailureCallback;
import moe.kyokobot.libdave.ffm.Callbacks;
import moe.kyokobot.libdave.ffm.FfmDecryptor;
import moe.kyokobot.libdave.ffm.FfmEncryptor;
import moe.kyokobot.libdave.ffm.FfmSession;
import moe.kyokobot.libdave.ffm.KyokoDave;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * {@link DaveFactory} backed by the Java Foreign Function &amp; Memory API (Java 22+).
 * <p>
 * The JVM must be allowed to call native code, e.g. with {@code --enable-native-access=ALL-UNNAMED}.
 */
public class FfmDaveFactory implements DaveFactory {
    /**
     * Loads the native library and ensures that {@link FfmDaveFactory} can be used on this platform.
     *
     * @throws RuntimeException if the native library could not be loaded and the factory is not safe to use.
     */
    public static void ensureAvailable() throws RuntimeException {
        try {
            KyokoDave.maxSupportedProtocolVersion();
        } catch (RuntimeException e) {
            throw e;
        } catch (LinkageError e) {
            throw new RuntimeException("DAVE FFM bindings could not be loaded!", e);
        }
    }

    /**
     * Sets a log sink for native code. This is optional and can be used to receive log messages from the native library.
     * @param sink an instance of {@link DaveLogSink} to receive log messages, or null to disable logging (default).
     */
    public static void setLogSink(@Nullable DaveLogSink sink) {
        if (sink == null) {
            KyokoDave.setLogSink(MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL);
            return;
        }

        KyokoDave.setLogSink(Callbacks.LOG_SINK, Callbacks.register(sink), Callbacks.USER_DATA_FREE);
    }

    @Override
    public int maxSupportedProtocolVersion() {
        return KyokoDave.maxSupportedProtocolVersion();
    }

    @Override
    public Decryptor createDecryptor() {
        return new FfmDecryptor(KyokoDave.decryptorCreate());
    }

    @Override
    public Encryptor createEncryptor() {
        return new FfmEncryptor(KyokoDave.encryptorCreate());
    }

    @Override
    public Session createSession(String context, String authSessionId, MLSFailureCallback callback) {
        // The context is unused: persisted keys are not supported by the bindings.
        MemorySegment handle;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment authSessionIdSegment =
                    authSessionId != null ? arena.allocateFrom(authSessionId) : MemorySegment.NULL;
            if (callback != null) {
                handle = KyokoDave.sessionCreate(authSessionIdSegment,
                        Callbacks.MLS_FAILURE, Callbacks.register(callback), Callbacks.USER_DATA_FREE);
            } else {
                handle = KyokoDave.sessionCreate(authSessionIdSegment,
                        MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL);
            }
        }
        return new FfmSession(handle);
    }
}
