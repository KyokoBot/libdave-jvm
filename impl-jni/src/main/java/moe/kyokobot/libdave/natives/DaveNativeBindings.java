package moe.kyokobot.libdave.natives;

import com.sedmelluq.lava.common.natives.NativeLibraryLoader;
import moe.kyokobot.libdave.RosterMap;
import moe.kyokobot.libdave.callbacks.DaveLogSink;
import moe.kyokobot.libdave.callbacks.EncryptorProtocolVersionChangedCallback;
import moe.kyokobot.libdave.callbacks.MLSFailureCallback;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class DaveNativeBindings {
    private static final NativeLibraryLoader nativeLoader =
            NativeLibraryLoader.create(DaveNativeBindings.class, "dave-jvm");

    private DaveNativeBindings() {
    }

    private static boolean isAndroid() {
        try {
            Class.forName("android.os.Build");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static DaveNativeBindings inst() {
        if (!isAndroid()) {
            nativeLoader.load();
        }
        return new DaveNativeBindings();
    }

    // Logging
    public native void daveSetLogSink(DaveLogSink sink);

    // Session
    public native int daveMaxSupportedProtocolVersion();

    public native long daveSessionCreate(String context, String authSessionId, MLSFailureCallback callback);

    public native void daveSessionDestroy(long sessionHandle);

    public native void daveSessionInit(long sessionHandle, int version, long groupId, String selfUserId);

    public native void daveSessionReset(long sessionHandle);

    public native void daveSessionSetProtocolVersion(long sessionHandle, int version);

    public native int daveSessionGetProtocolVersion(long sessionHandle);

    public native byte[] daveSessionGetLastEpochAuthenticator(long sessionHandle);

    public native void daveSessionSetExternalSender(long sessionHandle, byte[] externalSender);

    // Returns commitWelcomeBytes
    public native byte[] daveSessionProcessProposals(long sessionHandle, byte[] proposals, String[] recognizedUserIds);

    // Returns either RosterMap or (Integer)RESULT_FAILED / (Integer)RESULT_IGNORED
    public native Object daveSessionProcessCommit(long sessionHandle, byte[] commit);

    // Returns a RosterMap or null on failure.
    public native RosterMap daveSessionProcessWelcome(long sessionHandle, byte[] welcome, String[] recognizedUserIds);

    public native byte[] daveSessionGetMarshalledKeyPackage(long sessionHandle);

    public native long daveSessionGetKeyRatchet(long sessionHandle, String userId); // Returns DAVEKeyRatchetHandle

    public native void daveSessionGetPairwiseFingerprint(long sessionHandle, int version, String userId, Consumer<byte[]> callback);

    // Key Ratchet
    public native byte[] daveKeyRatchetGetEncryptionKey(long keyRatchetHandle, int keyGeneration);

    public native void daveKeyRatchetDeleteKey(long keyRatchetHandle, int keyGeneration);

    public native void daveKeyRatchetDestroy(long keyRatchetHandle);

    // Encryptor
    public native long daveEncryptorCreate();

    public native void daveEncryptorDestroy(long encryptorHandle);

    public native void daveEncryptorSetKeyRatchet(long encryptorHandle, long keyRatchetHandle);

    public native void daveEncryptorSetPassthroughMode(long encryptorHandle, boolean passthroughMode);

    public native void daveEncryptorAssignSsrcToCodec(long encryptorHandle, int ssrc, int codecType); // codecType as int from Enum

    public native int daveEncryptorGetProtocolVersion(long encryptorHandle);

    public native long daveEncryptorGetMaxCiphertextByteSize(long encryptorHandle, int mediaType, long frameSize);

    // Encrypt - Returns number of bytes written, or negative value on error (negated result code from DAVEEncryptorResultCode)
    public native int daveEncryptorEncrypt(long encryptorHandle, int mediaType, int ssrc, byte[] frame, byte[] encryptedFrame);

    public native int daveEncryptorEncrypt(long encryptorHandle, int mediaType, int ssrc, ByteBuffer frame, ByteBuffer encryptedFrame);

    public native int daveEncryptorEncrypt(long encryptorHandle, int mediaType, int ssrc, long framePtr, int frameSize, long encryptedFramePtr, int encryptedFrameCapacity);

    public native void daveEncryptorSetProtocolVersionChangedCallback(long encryptorHandle, EncryptorProtocolVersionChangedCallback callback);

    // Decryptor
    public native long daveDecryptorCreate();

    public native void daveDecryptorDestroy(long decryptorHandle);

    public native void daveDecryptorTransitionToKeyRatchet(long decryptorHandle, long keyRatchetHandle);

    public native void daveDecryptorTransitionToPassthroughMode(long decryptorHandle, boolean passthroughMode);

    // Decrypt - Returns bytes written, or negative value on error (negated result code from DAVEDecryptorResultCode)
    public native int daveDecryptorDecrypt(long decryptorHandle, int mediaType, byte[] encryptedFrame, byte[] frame);

    public native int daveDecryptorDecrypt(long decryptorHandle, int mediaType, ByteBuffer encryptedFrame, ByteBuffer frame);

    public native int daveDecryptorDecrypt(long decryptorHandle, int mediaType, long encryptedFramePtr, int encryptedFrameSize, long framePtr, int frameCapacity);

    public native long daveDecryptorGetMaxPlaintextByteSize(long decryptorHandle, int mediaType, long encryptedFrameSize);
}
