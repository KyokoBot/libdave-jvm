package moe.kyokobot.libdave.impl;

import moe.kyokobot.libdave.Codec;
import moe.kyokobot.libdave.Encryptor;
import moe.kyokobot.libdave.KeyRatchet;
import moe.kyokobot.libdave.MediaType;
import moe.kyokobot.libdave.callbacks.EncryptorProtocolVersionChangedCallback;
import moe.kyokobot.libdave.natives.DaveNativeBindings;

import java.nio.ByteBuffer;
import java.util.Objects;

public class NativeEncryptor extends DaveNativeHandle implements Encryptor {
    public NativeEncryptor(long handle) {
        super(handle);
    }

    @Override
    public void setKeyRatchet(KeyRatchet keyRatchet) {
        assertOpen();
        if (keyRatchet instanceof NativeKeyRatchet) {
            NativeKeyRatchet nativeRatchet = (NativeKeyRatchet) keyRatchet;
            long ratchetHandle = HandleStealer.stealHandle(nativeRatchet);
            try {
                DaveNativeBindings.inst().daveEncryptorSetKeyRatchet(handle, ratchetHandle);
            } catch (Throwable t) {
                DaveNativeBindings.inst().daveKeyRatchetDestroy(ratchetHandle);
                throw t;
            }
        } else {
            throw new IllegalArgumentException("The passed KeyRatchet was not created by native Session!");
        }
    }

    @Override
    public void setPassthroughMode(boolean passthroughMode) {
        assertOpen();
        DaveNativeBindings.inst().daveEncryptorSetPassthroughMode(handle, passthroughMode);
    }

    @Override
    public void assignSsrcToCodec(int ssrc, Codec codec) {
        assertOpen();
        DaveNativeBindings.inst().daveEncryptorAssignSsrcToCodec(handle, ssrc, codec.getValue());
    }

    @Override
    public int getProtocolVersion() {
        assertOpen();
        return DaveNativeBindings.inst().daveEncryptorGetProtocolVersion(handle);
    }

    @Override
    public int encrypt(MediaType mediaType, int ssrc, byte[] frame, byte[] encryptedFrame) {
        assertOpen();
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(encryptedFrame, "encryptedFrame");
        return DaveNativeBindings.inst().daveEncryptorEncrypt(handle, mediaType.getValue(), ssrc, frame, encryptedFrame);
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
        return DaveNativeBindings.inst().daveEncryptorEncrypt(handle, mediaType.getValue(), ssrc, frame, encryptedFrame);
    }

    @Override
    public void setProtocolVersionChangedCallback(EncryptorProtocolVersionChangedCallback callback) {
        assertOpen();
        DaveNativeBindings.inst().daveEncryptorSetProtocolVersionChangedCallback(handle, callback);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        DaveNativeBindings.inst().daveEncryptorDestroy(handle);
    }
}

