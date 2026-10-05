package moe.kyokobot.libdave.ffm;

import org.jetbrains.annotations.ApiStatus;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static java.lang.foreign.ValueLayout.*;

/**
 * Raw downcalls into the {@code kyoko_dave_*} C ABI (see {@code natives/src/main/cpp/kyoko_dave.h}).
 * <p>
 * {@code size_t} values are always exposed as {@code long}, regardless of the platform's pointer width.
 */
@ApiStatus.Internal
public final class KyokoDave {
    private static final Linker LINKER = Linker.nativeLinker();
    // Named so it can be told apart from int32_t/int64_t when widening to long below.
    static final ValueLayout SIZE_T = ((ValueLayout) LINKER.canonicalLayouts().get("size_t")).withName("size_t");
    private static final SymbolLookup LOOKUP = NativeLibrary.load();

    private static final MethodHandle FREE = downcall("kyoko_dave_free",
            FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle MAX_SUPPORTED_PROTOCOL_VERSION = downcall("kyoko_dave_max_supported_protocol_version",
            FunctionDescriptor.of(JAVA_SHORT));
    private static final MethodHandle SET_LOG_SINK = downcall("kyoko_dave_set_log_sink",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));

    private static final MethodHandle SESSION_CREATE = downcall("kyoko_dave_session_create",
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle SESSION_DESTROY = downcall("kyoko_dave_session_destroy",
            FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle SESSION_INIT = downcall("kyoko_dave_session_init",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_SHORT, JAVA_LONG, ADDRESS));
    private static final MethodHandle SESSION_RESET = downcall("kyoko_dave_session_reset",
            FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle SESSION_SET_PROTOCOL_VERSION = downcall("kyoko_dave_session_set_protocol_version",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_SHORT));
    private static final MethodHandle SESSION_GET_PROTOCOL_VERSION = downcall("kyoko_dave_session_get_protocol_version",
            FunctionDescriptor.of(JAVA_SHORT, ADDRESS));
    private static final MethodHandle SESSION_GET_LAST_EPOCH_AUTHENTICATOR = downcall("kyoko_dave_session_get_last_epoch_authenticator",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle SESSION_SET_EXTERNAL_SENDER = downcall("kyoko_dave_session_set_external_sender",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, SIZE_T));
    private static final MethodHandle SESSION_PROCESS_PROPOSALS = downcall("kyoko_dave_session_process_proposals",
            FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, SIZE_T, ADDRESS, SIZE_T, ADDRESS, ADDRESS));
    private static final MethodHandle SESSION_PROCESS_COMMIT = downcall("kyoko_dave_session_process_commit",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, SIZE_T, ADDRESS));
    private static final MethodHandle SESSION_PROCESS_WELCOME = downcall("kyoko_dave_session_process_welcome",
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, SIZE_T, ADDRESS, SIZE_T));
    private static final MethodHandle SESSION_GET_MARSHALLED_KEY_PACKAGE = downcall("kyoko_dave_session_get_marshalled_key_package",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle SESSION_GET_KEY_RATCHET = downcall("kyoko_dave_session_get_key_ratchet",
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle SESSION_GET_PAIRWISE_FINGERPRINT = downcall("kyoko_dave_session_get_pairwise_fingerprint",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_SHORT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    private static final MethodHandle ROSTER_SIZE = downcall("kyoko_dave_roster_size",
            FunctionDescriptor.of(SIZE_T, ADDRESS));
    private static final MethodHandle ROSTER_GET_USER_ID = downcall("kyoko_dave_roster_get_user_id",
            FunctionDescriptor.of(JAVA_LONG, ADDRESS, SIZE_T));
    private static final MethodHandle ROSTER_GET_KEY_SIZE = downcall("kyoko_dave_roster_get_key_size",
            FunctionDescriptor.of(SIZE_T, ADDRESS, SIZE_T));
    private static final MethodHandle ROSTER_GET_KEY_DATA = downcall("kyoko_dave_roster_get_key_data",
            FunctionDescriptor.of(ADDRESS, ADDRESS, SIZE_T));
    private static final MethodHandle ROSTER_DESTROY = downcall("kyoko_dave_roster_destroy",
            FunctionDescriptor.ofVoid(ADDRESS));

    private static final MethodHandle KEY_RATCHET_GET_ENCRYPTION_KEY = downcall("kyoko_dave_key_ratchet_get_encryption_key",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle KEY_RATCHET_DELETE_KEY = downcall("kyoko_dave_key_ratchet_delete_key",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));
    private static final MethodHandle KEY_RATCHET_DESTROY = downcall("kyoko_dave_key_ratchet_destroy",
            FunctionDescriptor.ofVoid(ADDRESS));

    private static final MethodHandle ENCRYPTOR_CREATE = downcall("kyoko_dave_encryptor_create",
            FunctionDescriptor.of(ADDRESS));
    private static final MethodHandle ENCRYPTOR_DESTROY = downcall("kyoko_dave_encryptor_destroy",
            FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle ENCRYPTOR_SET_KEY_RATCHET = downcall("kyoko_dave_encryptor_set_key_ratchet",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
    private static final MethodHandle ENCRYPTOR_SET_PASSTHROUGH_MODE = downcall("kyoko_dave_encryptor_set_passthrough_mode",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_BOOLEAN));
    private static final MethodHandle ENCRYPTOR_ASSIGN_SSRC_TO_CODEC = downcall("kyoko_dave_encryptor_assign_ssrc_to_codec",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT));
    private static final MethodHandle ENCRYPTOR_GET_PROTOCOL_VERSION = downcall("kyoko_dave_encryptor_get_protocol_version",
            FunctionDescriptor.of(JAVA_SHORT, ADDRESS));
    private static final MethodHandle ENCRYPTOR_ENCRYPT = downcall("kyoko_dave_encryptor_encrypt",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, SIZE_T, ADDRESS, SIZE_T));
    private static final MethodHandle ENCRYPTOR_SET_PROTOCOL_VERSION_CHANGED_CALLBACK = downcall("kyoko_dave_encryptor_set_protocol_version_changed_callback",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    private static final MethodHandle DECRYPTOR_CREATE = downcall("kyoko_dave_decryptor_create",
            FunctionDescriptor.of(ADDRESS));
    private static final MethodHandle DECRYPTOR_DESTROY = downcall("kyoko_dave_decryptor_destroy",
            FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle DECRYPTOR_TRANSITION_TO_KEY_RATCHET = downcall("kyoko_dave_decryptor_transition_to_key_ratchet",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
    private static final MethodHandle DECRYPTOR_TRANSITION_TO_PASSTHROUGH_MODE = downcall("kyoko_dave_decryptor_transition_to_passthrough_mode",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_BOOLEAN));
    private static final MethodHandle DECRYPTOR_DECRYPT = downcall("kyoko_dave_decryptor_decrypt",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, SIZE_T, ADDRESS, SIZE_T));

    static {
        // Match the JNI bindings: discard libdave's log output until a sink is set.
        setLogSink(MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL);
    }

    private KyokoDave() {
    }

    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = LOOKUP.find(name)
                .orElseThrow(() -> new UnsatisfiedLinkError("Native symbol not found: " + name));
        MethodHandle handle = LINKER.downcallHandle(symbol, descriptor);
        return MethodHandles.explicitCastArguments(handle, javaType(descriptor));
    }

    /**
     * Returns the descriptor's method type with every {@code size_t} widened to {@code long}.
     */
    static MethodType javaType(FunctionDescriptor descriptor) {
        MethodType type = descriptor.toMethodType();
        for (int i = 0; i < descriptor.argumentLayouts().size(); i++) {
            if (descriptor.argumentLayouts().get(i).equals(SIZE_T)) {
                type = type.changeParameterType(i, long.class);
            }
        }
        if (descriptor.returnLayout().filter(SIZE_T::equals).isPresent()) {
            type = type.changeReturnType(long.class);
        }
        return type;
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException e) {
            throw e;
        }
        if (t instanceof Error e) {
            throw e;
        }
        throw new IllegalStateException(t);
    }

    public static MemorySegment allocateSize(Arena arena) {
        return arena.allocate(SIZE_T);
    }

    public static long readSize(MemorySegment size) {
        return SIZE_T.byteSize() == Integer.BYTES
                ? Integer.toUnsignedLong(size.get(JAVA_INT, 0))
                : size.get(JAVA_LONG, 0);
    }

    // General

    public static void free(MemorySegment ptr) {
        try {
            FREE.invokeExact(ptr);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int maxSupportedProtocolVersion() {
        try {
            return Short.toUnsignedInt((short) MAX_SUPPORTED_PROTOCOL_VERSION.invokeExact());
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void setLogSink(MemorySegment callback, MemorySegment userData, MemorySegment userDataFree) {
        try {
            SET_LOG_SINK.invokeExact(callback, userData, userDataFree);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    // Session

    public static MemorySegment sessionCreate(MemorySegment authSessionId, MemorySegment callback,
                                       MemorySegment userData, MemorySegment userDataFree) {
        try {
            return (MemorySegment) SESSION_CREATE.invokeExact(authSessionId, callback, userData, userDataFree);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void sessionDestroy(MemorySegment session) {
        try {
            SESSION_DESTROY.invokeExact(session);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void sessionInit(MemorySegment session, int version, long groupId, MemorySegment selfUserId) {
        try {
            SESSION_INIT.invokeExact(session, (short) version, groupId, selfUserId);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void sessionReset(MemorySegment session) {
        try {
            SESSION_RESET.invokeExact(session);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void sessionSetProtocolVersion(MemorySegment session, int version) {
        try {
            SESSION_SET_PROTOCOL_VERSION.invokeExact(session, (short) version);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int sessionGetProtocolVersion(MemorySegment session) {
        try {
            return Short.toUnsignedInt((short) SESSION_GET_PROTOCOL_VERSION.invokeExact(session));
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void sessionGetLastEpochAuthenticator(MemorySegment session, MemorySegment outData, MemorySegment outSize) {
        try {
            SESSION_GET_LAST_EPOCH_AUTHENTICATOR.invokeExact(session, outData, outSize);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void sessionSetExternalSender(MemorySegment session, MemorySegment externalSender, long externalSenderSize) {
        try {
            SESSION_SET_EXTERNAL_SENDER.invokeExact(session, externalSender, externalSenderSize);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static boolean sessionProcessProposals(MemorySegment session, MemorySegment proposals, long proposalsSize,
                                           MemorySegment recognizedUserIds, long recognizedUserIdsCount,
                                           MemorySegment outData, MemorySegment outSize) {
        try {
            return (boolean) SESSION_PROCESS_PROPOSALS.invokeExact(session, proposals, proposalsSize,
                    recognizedUserIds, recognizedUserIdsCount, outData, outSize);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int sessionProcessCommit(MemorySegment session, MemorySegment commit, long commitSize,
                                    MemorySegment outRoster) {
        try {
            return (int) SESSION_PROCESS_COMMIT.invokeExact(session, commit, commitSize, outRoster);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static MemorySegment sessionProcessWelcome(MemorySegment session, MemorySegment welcome, long welcomeSize,
                                               MemorySegment recognizedUserIds, long recognizedUserIdsCount) {
        try {
            return (MemorySegment) SESSION_PROCESS_WELCOME.invokeExact(session, welcome, welcomeSize,
                    recognizedUserIds, recognizedUserIdsCount);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void sessionGetMarshalledKeyPackage(MemorySegment session, MemorySegment outData, MemorySegment outSize) {
        try {
            SESSION_GET_MARSHALLED_KEY_PACKAGE.invokeExact(session, outData, outSize);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static MemorySegment sessionGetKeyRatchet(MemorySegment session, MemorySegment userId) {
        try {
            return (MemorySegment) SESSION_GET_KEY_RATCHET.invokeExact(session, userId);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void sessionGetPairwiseFingerprint(MemorySegment session, int version, MemorySegment userId,
                                              MemorySegment callback, MemorySegment userData,
                                              MemorySegment userDataFree) {
        try {
            SESSION_GET_PAIRWISE_FINGERPRINT.invokeExact(session, (short) version, userId, callback, userData,
                    userDataFree);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    // Roster

    public static long rosterSize(MemorySegment roster) {
        try {
            return (long) ROSTER_SIZE.invokeExact(roster);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long rosterGetUserId(MemorySegment roster, long index) {
        try {
            return (long) ROSTER_GET_USER_ID.invokeExact(roster, index);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static long rosterGetKeySize(MemorySegment roster, long index) {
        try {
            return (long) ROSTER_GET_KEY_SIZE.invokeExact(roster, index);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static MemorySegment rosterGetKeyData(MemorySegment roster, long index) {
        try {
            return (MemorySegment) ROSTER_GET_KEY_DATA.invokeExact(roster, index);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void rosterDestroy(MemorySegment roster) {
        try {
            ROSTER_DESTROY.invokeExact(roster);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    // Key ratchet

    public static void keyRatchetGetEncryptionKey(MemorySegment keyRatchet, int keyGeneration, MemorySegment outData,
                                           MemorySegment outSize) {
        try {
            KEY_RATCHET_GET_ENCRYPTION_KEY.invokeExact(keyRatchet, keyGeneration, outData, outSize);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void keyRatchetDeleteKey(MemorySegment keyRatchet, int keyGeneration) {
        try {
            KEY_RATCHET_DELETE_KEY.invokeExact(keyRatchet, keyGeneration);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void keyRatchetDestroy(MemorySegment keyRatchet) {
        try {
            KEY_RATCHET_DESTROY.invokeExact(keyRatchet);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    // Encryptor

    public static MemorySegment encryptorCreate() {
        try {
            return (MemorySegment) ENCRYPTOR_CREATE.invokeExact();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void encryptorDestroy(MemorySegment encryptor) {
        try {
            ENCRYPTOR_DESTROY.invokeExact(encryptor);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void encryptorSetKeyRatchet(MemorySegment encryptor, MemorySegment keyRatchet) {
        try {
            ENCRYPTOR_SET_KEY_RATCHET.invokeExact(encryptor, keyRatchet);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void encryptorSetPassthroughMode(MemorySegment encryptor, boolean passthroughMode) {
        try {
            ENCRYPTOR_SET_PASSTHROUGH_MODE.invokeExact(encryptor, passthroughMode);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void encryptorAssignSsrcToCodec(MemorySegment encryptor, int ssrc, int codec) {
        try {
            ENCRYPTOR_ASSIGN_SSRC_TO_CODEC.invokeExact(encryptor, ssrc, codec);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int encryptorGetProtocolVersion(MemorySegment encryptor) {
        try {
            return Short.toUnsignedInt((short) ENCRYPTOR_GET_PROTOCOL_VERSION.invokeExact(encryptor));
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int encryptorEncrypt(MemorySegment encryptor, int mediaType, int ssrc, MemorySegment frame,
                                long frameSize, MemorySegment encryptedFrame, long encryptedFrameCapacity) {
        try {
            return (int) ENCRYPTOR_ENCRYPT.invokeExact(encryptor, mediaType, ssrc, frame, frameSize, encryptedFrame,
                    encryptedFrameCapacity);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void encryptorSetProtocolVersionChangedCallback(MemorySegment encryptor, MemorySegment callback,
                                                           MemorySegment userData, MemorySegment userDataFree) {
        try {
            ENCRYPTOR_SET_PROTOCOL_VERSION_CHANGED_CALLBACK.invokeExact(encryptor, callback, userData, userDataFree);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    // Decryptor

    public static MemorySegment decryptorCreate() {
        try {
            return (MemorySegment) DECRYPTOR_CREATE.invokeExact();
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void decryptorDestroy(MemorySegment decryptor) {
        try {
            DECRYPTOR_DESTROY.invokeExact(decryptor);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void decryptorTransitionToKeyRatchet(MemorySegment decryptor, MemorySegment keyRatchet) {
        try {
            DECRYPTOR_TRANSITION_TO_KEY_RATCHET.invokeExact(decryptor, keyRatchet);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static void decryptorTransitionToPassthroughMode(MemorySegment decryptor, boolean passthroughMode) {
        try {
            DECRYPTOR_TRANSITION_TO_PASSTHROUGH_MODE.invokeExact(decryptor, passthroughMode);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int decryptorDecrypt(MemorySegment decryptor, int mediaType, MemorySegment encryptedFrame,
                                long encryptedFrameSize, MemorySegment frame, long frameCapacity) {
        try {
            return (int) DECRYPTOR_DECRYPT.invokeExact(decryptor, mediaType, encryptedFrame, encryptedFrameSize, frame,
                    frameCapacity);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }
}
