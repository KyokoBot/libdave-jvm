package moe.kyokobot.libdave.ffm;

import moe.kyokobot.libdave.KeyRatchet;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.ADDRESS;

public class FfmKeyRatchet extends FfmHandle implements KeyRatchet {
    FfmKeyRatchet(MemorySegment handle) {
        super(handle);
    }

    @Override
    public byte[] getEncryptionKey(int keyGeneration) {
        assertOpen();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outData = arena.allocate(ADDRESS);
            MemorySegment outSize = KyokoDave.allocateSize(arena);
            KyokoDave.keyRatchetGetEncryptionKey(handle, keyGeneration, outData, outSize);
            return takeBytes(outData, outSize);
        }
    }

    @Override
    public void deleteKey(int keyGeneration) {
        assertOpen();
        KyokoDave.keyRatchetDeleteKey(handle, keyGeneration);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        KyokoDave.keyRatchetDestroy(handle);
    }
}
