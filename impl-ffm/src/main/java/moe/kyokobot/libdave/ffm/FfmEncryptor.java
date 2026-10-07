package moe.kyokobot.libdave.ffm;

import moe.kyokobot.libdave.Codec;
import moe.kyokobot.libdave.Encryptor;
import moe.kyokobot.libdave.KeyRatchet;
import moe.kyokobot.libdave.MediaType;
import moe.kyokobot.libdave.callbacks.EncryptorProtocolVersionChangedCallback;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class FfmEncryptor extends FfmHandle implements Encryptor {
    public FfmEncryptor(MemorySegment handle) {
        super(handle);
    }

    @Override
    public void setKeyRatchet(KeyRatchet keyRatchet) {
        assertOpen();
        if (keyRatchet instanceof FfmKeyRatchet ffmRatchet) {
            MemorySegment ratchetHandle = ffmRatchet.stealHandle();
            try {
                KyokoDave.encryptorSetKeyRatchet(handle, ratchetHandle);
            } catch (Throwable t) {
                KyokoDave.keyRatchetDestroy(ratchetHandle);
                throw t;
            }
        } else {
            throw new IllegalArgumentException("The passed KeyRatchet was not created by FFM Session!");
        }
    }

    @Override
    public void setPassthroughMode(boolean passthroughMode) {
        assertOpen();
        KyokoDave.encryptorSetPassthroughMode(handle, passthroughMode);
    }

    @Override
    public void assignSsrcToCodec(int ssrc, Codec codec) {
        assertOpen();
        KyokoDave.encryptorAssignSsrcToCodec(handle, ssrc, codec.getValue());
    }

    @Override
    public int getProtocolVersion() {
        assertOpen();
        return KyokoDave.encryptorGetProtocolVersion(handle);
    }

    @Override
    public int encrypt(MediaType mediaType, int ssrc, byte[] frame, byte[] encryptedFrame) {
        assertOpen();
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(encryptedFrame, "encryptedFrame");
        // libdave may log or invoke callbacks while encrypting, which rules out critical downcalls
        // (and with them, passing heap arrays directly), so copy through native memory instead.
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment frameSegment = allocateBytes(arena, frame);
            MemorySegment encryptedFrameSegment = arena.allocate(encryptedFrame.length);
            int result = KyokoDave.encryptorEncrypt(handle, mediaType.getValue(), ssrc,
                    frameSegment, frame.length, encryptedFrameSegment, encryptedFrame.length);
            if (result > 0) {
                MemorySegment.copy(encryptedFrameSegment, JAVA_BYTE, 0, encryptedFrame, 0, result);
            }
            return result;
        }
    }

    @Override
    public int encrypt(MediaType mediaType, int ssrc, ByteBuffer frame, ByteBuffer encryptedFrame) {
        assertOpen();
        if (!frame.isDirect()) {
            throw new IllegalArgumentException("frame must be backed by a direct buffer");
        }
        if (!encryptedFrame.isDirect()) {
            throw new IllegalArgumentException("encryptedFrame must be backed by a direct buffer");
        }
        return KyokoDave.encryptorEncrypt(handle, mediaType.getValue(), ssrc,
                MemorySegment.ofBuffer(frame), frame.remaining(),
                MemorySegment.ofBuffer(encryptedFrame), encryptedFrame.remaining());
    }

    @Override
    public void setProtocolVersionChangedCallback(EncryptorProtocolVersionChangedCallback callback) {
        assertOpen();
        if (callback == null) {
            KyokoDave.encryptorSetProtocolVersionChangedCallback(handle,
                    MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL);
            return;
        }

        MemorySegment userData = Callbacks.register(callback);
        KyokoDave.encryptorSetProtocolVersionChangedCallback(handle,
                Callbacks.PROTOCOL_VERSION_CHANGED, userData, Callbacks.USER_DATA_FREE);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        KyokoDave.encryptorDestroy(handle);
    }
}
