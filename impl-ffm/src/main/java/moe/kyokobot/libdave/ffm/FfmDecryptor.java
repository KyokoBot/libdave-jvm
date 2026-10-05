package moe.kyokobot.libdave.ffm;

import moe.kyokobot.libdave.Decryptor;
import moe.kyokobot.libdave.KeyRatchet;
import moe.kyokobot.libdave.MediaType;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class FfmDecryptor extends FfmHandle implements Decryptor {
    public FfmDecryptor(MemorySegment handle) {
        super(handle);
    }

    @Override
    public void transitionToKeyRatchet(KeyRatchet keyRatchet) {
        assertOpen();
        if (keyRatchet instanceof FfmKeyRatchet ffmRatchet) {
            MemorySegment ratchetHandle = ffmRatchet.stealHandle();
            try {
                KyokoDave.decryptorTransitionToKeyRatchet(handle, ratchetHandle);
            } catch (Throwable t) {
                KyokoDave.keyRatchetDestroy(ratchetHandle);
                throw t;
            }
        } else {
            throw new IllegalArgumentException("The passed KeyRatchet was not created by FFM Session!");
        }
    }

    @Override
    public void transitionToPassthroughMode(boolean passthroughMode) {
        assertOpen();
        KyokoDave.decryptorTransitionToPassthroughMode(handle, passthroughMode);
    }

    @Override
    public int decrypt(MediaType mediaType, byte[] encryptedFrame, byte[] frame) {
        assertOpen();
        // See FfmEncryptor#encrypt(MediaType, int, byte[], byte[]) for why this copies.
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment encryptedFrameSegment = allocateBytes(arena, encryptedFrame);
            MemorySegment frameSegment = arena.allocate(frame.length);
            int result = KyokoDave.decryptorDecrypt(handle, mediaType.getValue(),
                    encryptedFrameSegment, encryptedFrame.length, frameSegment, frame.length);
            if (result > 0) {
                MemorySegment.copy(frameSegment, JAVA_BYTE, 0, frame, 0, result);
            }
            return result;
        }
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
        return KyokoDave.decryptorDecrypt(handle, mediaType.getValue(),
                MemorySegment.ofBuffer(encryptedFrame), encryptedFrame.remaining(),
                MemorySegment.ofBuffer(frame), frame.remaining());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        KyokoDave.decryptorDestroy(handle);
    }
}
