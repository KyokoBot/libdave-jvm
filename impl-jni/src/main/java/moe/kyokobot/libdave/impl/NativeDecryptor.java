package moe.kyokobot.libdave.impl;

import moe.kyokobot.libdave.Decryptor;
import moe.kyokobot.libdave.KeyRatchet;
import moe.kyokobot.libdave.MediaType;
import moe.kyokobot.libdave.natives.DaveNativeBindings;

import java.nio.ByteBuffer;
import java.util.Objects;

public class NativeDecryptor extends DaveNativeHandle implements Decryptor {
    public NativeDecryptor(long handle) {
        super(handle);
    }

    @Override
    public void transitionToKeyRatchet(KeyRatchet keyRatchet) {
        assertOpen();
        if (keyRatchet instanceof NativeKeyRatchet) {
            NativeKeyRatchet nativeRatchet = (NativeKeyRatchet) keyRatchet;
            long ratchetHandle = HandleStealer.stealHandle(nativeRatchet);
            try {
                DaveNativeBindings.inst().daveDecryptorTransitionToKeyRatchet(handle, ratchetHandle);
            } catch (Throwable t) {
                DaveNativeBindings.inst().daveKeyRatchetDestroy(ratchetHandle);
                throw t;
            }
        } else {
            throw new IllegalArgumentException("The passed KeyRatchet was not created by native Session!");
        }
    }

    @Override
    public void transitionToPassthroughMode(boolean passthroughMode) {
        assertOpen();
        DaveNativeBindings.inst().daveDecryptorTransitionToPassthroughMode(handle, passthroughMode);
    }

    @Override
    public int decrypt(MediaType mediaType, byte[] encryptedFrame, byte[] frame) {
        assertOpen();
        Objects.requireNonNull(encryptedFrame, "encryptedFrame");
        Objects.requireNonNull(frame, "frame");
        return DaveNativeBindings.inst().daveDecryptorDecrypt(handle, mediaType.getValue(), encryptedFrame, frame);
    }

    @Override
    public int decrypt(MediaType mediaType, ByteBuffer encryptedFrame, ByteBuffer frame) {
        assertOpen();
        if (!frame.isDirect()) {
            throw new IllegalArgumentException("frame must be backed by a direct buffer");
        }
        if (!encryptedFrame.isDirect()) {
            throw new IllegalArgumentException("encryptedFrame must be backed by a direct buffer");
        }
        return DaveNativeBindings.inst().daveDecryptorDecrypt(handle, mediaType.getValue(), encryptedFrame, frame);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        DaveNativeBindings.inst().daveDecryptorDestroy(handle);
    }
}

