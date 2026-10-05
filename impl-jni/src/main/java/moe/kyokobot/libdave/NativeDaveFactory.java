package moe.kyokobot.libdave;

import moe.kyokobot.libdave.callbacks.DaveLogSink;
import moe.kyokobot.libdave.callbacks.MLSFailureCallback;
import moe.kyokobot.libdave.impl.NativeDecryptor;
import moe.kyokobot.libdave.impl.NativeEncryptor;
import moe.kyokobot.libdave.impl.NativeSession;
import moe.kyokobot.libdave.natives.DaveNativeBindings;
import org.jetbrains.annotations.Nullable;

/**
 * {@link DaveFactory} backed by JNI bindings to the native DAVE library.
 */
public class NativeDaveFactory implements DaveFactory {
    /**
     * Creates a new factory. The native library is loaded on first use.
     */
    public NativeDaveFactory() {
    }

    /**
     * Loads the native library and ensures that {@link NativeDaveFactory} can be used on this platform.
     *
     * @throws RuntimeException if the native library could not be loaded and the factory is not safe to use.
     */
    public static void ensureAvailable() throws RuntimeException {
        try {
            DaveNativeBindings.inst();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("DAVE Native bindings could not be loaded!", e);
        }
    }

    /**
     * Sets a log sink for native code. This is optional and can be used to receive log messages from the native library.
     * @param sink an instance of {@link DaveLogSink} to receive log messages, or null to disable logging (default).
     */
    public static void setLogSink(@Nullable DaveLogSink sink) {
        DaveNativeBindings.inst().daveSetLogSink(sink);
    }

    @Override
    public int maxSupportedProtocolVersion() {
        return DaveNativeBindings.inst().daveMaxSupportedProtocolVersion();
    }

    @Override
    public Decryptor createDecryptor() {
        long handle = DaveNativeBindings.inst().daveDecryptorCreate();
        return new NativeDecryptor(handle);
    }

    @Override
    public Encryptor createEncryptor() {
        long handle = DaveNativeBindings.inst().daveEncryptorCreate();
        return new NativeEncryptor(handle);
    }

    @Override
    public Session createSession(String context, String authSessionId, MLSFailureCallback callback) {
        long handle = DaveNativeBindings.inst().daveSessionCreate(context, authSessionId, callback);
        return new NativeSession(handle);
    }
}
