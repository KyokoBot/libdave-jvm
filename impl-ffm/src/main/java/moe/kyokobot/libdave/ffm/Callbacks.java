package moe.kyokobot.libdave.ffm;

import moe.kyokobot.libdave.callbacks.DaveLogSink;
import moe.kyokobot.libdave.callbacks.EncryptorProtocolVersionChangedCallback;
import moe.kyokobot.libdave.callbacks.MLSFailureCallback;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static java.lang.foreign.ValueLayout.*;

/**
 * Upcall stubs for the {@code kyoko_dave_*} callbacks.
 * <p>
 * Each stub is created once. The Java callback is looked up through the {@code user_data} pointer, which carries a
 * registry id rather than a real address, and is dropped from the registry when the native side calls
 * {@code user_data_free}.
 */
@ApiStatus.Internal
public final class Callbacks {
    private static final AtomicLong NEXT_ID = new AtomicLong(1);
    private static final Map<Long, Object> CALLBACKS = new ConcurrentHashMap<>();

    public static final MemorySegment USER_DATA_FREE = upcall("userDataFree",
            FunctionDescriptor.ofVoid(ADDRESS));
    public static final MemorySegment LOG_SINK = upcall("logSink",
            FunctionDescriptor.ofVoid(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    public static final MemorySegment MLS_FAILURE = upcall("mlsFailure",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));
    public static final MemorySegment PAIRWISE_FINGERPRINT = upcall("pairwiseFingerprint",
            FunctionDescriptor.ofVoid(ADDRESS, KyokoDave.SIZE_T, ADDRESS));
    public static final MemorySegment PROTOCOL_VERSION_CHANGED = upcall("protocolVersionChanged",
            FunctionDescriptor.ofVoid(ADDRESS));

    private Callbacks() {
    }

    private static MemorySegment upcall(String name, FunctionDescriptor descriptor) {
        try {
            MethodHandle target = MethodHandles.lookup().findStatic(Callbacks.class, name,
                    KyokoDave.javaType(descriptor));
            target = MethodHandles.explicitCastArguments(target, descriptor.toMethodType());
            return Linker.nativeLinker().upcallStub(target, descriptor, Arena.global());
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Registers a callback and returns the {@code user_data} to pass along with it. The registration is released
     * through {@link #USER_DATA_FREE}.
     */
    public static MemorySegment register(Object callback) {
        long id = NEXT_ID.getAndIncrement();
        CALLBACKS.put(id, callback);
        return MemorySegment.ofAddress(id);
    }

    @SuppressWarnings("unchecked")
    private static <T> @Nullable T lookup(MemorySegment userData) {
        return (T) CALLBACKS.get(userData.address());
    }

    private static String readString(MemorySegment string) {
        if (string.address() == 0) {
            return "";
        }
        return string.reinterpret(Long.MAX_VALUE).getString(0);
    }

    // An exception escaping an upcall crashes the JVM.
    private static void report(Throwable t) {
        Thread thread = Thread.currentThread();
        thread.getUncaughtExceptionHandler().uncaughtException(thread, t);
    }

    private static void userDataFree(MemorySegment userData) {
        CALLBACKS.remove(userData.address());
    }

    private static void logSink(int severity, MemorySegment file, int line, MemorySegment message,
                                MemorySegment userData) {
        try {
            DaveLogSink sink = lookup(userData);
            if (sink != null) {
                sink.log(severity, readString(file), line, readString(message));
            }
        } catch (Throwable t) {
            report(t);
        }
    }

    private static void mlsFailure(MemorySegment source, MemorySegment reason, MemorySegment userData) {
        try {
            MLSFailureCallback callback = lookup(userData);
            if (callback != null) {
                callback.onFailure(readString(source), readString(reason));
            }
        } catch (Throwable t) {
            report(t);
        }
    }

    private static void pairwiseFingerprint(MemorySegment fingerprint, long fingerprintSize,
                                            MemorySegment userData) {
        try {
            Consumer<byte[]> callback = lookup(userData);
            if (callback != null) {
                callback.accept(fingerprint.reinterpret(fingerprintSize).toArray(JAVA_BYTE));
            }
        } catch (Throwable t) {
            report(t);
        }
    }

    private static void protocolVersionChanged(MemorySegment userData) {
        try {
            EncryptorProtocolVersionChangedCallback callback = lookup(userData);
            if (callback != null) {
                callback.onChanged();
            }
        } catch (Throwable t) {
            report(t);
        }
    }
}
