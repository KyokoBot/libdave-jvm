package moe.kyokobot.libdave.ffm;

import org.jetbrains.annotations.ApiStatus;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

@ApiStatus.Internal
public abstract class FfmHandle implements AutoCloseable {
    protected final MemorySegment handle;
    protected volatile boolean closed;

    protected FfmHandle(MemorySegment handle) {
        if (handle == null || handle.address() == 0) {
            throw new IllegalArgumentException("Handle cannot be a null pointer");
        }
        this.handle = handle;
    }

    protected void assertOpen() {
        if (closed) {
            throw new IllegalStateException("This object has been closed");
        }
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * Transfers ownership of the native handle to the caller and marks this object as closed.
     */
    @ApiStatus.Internal
    public MemorySegment stealHandle() {
        assertOpen();
        this.closed = true;
        return handle;
    }

    /**
     * Copies a buffer returned through an {@code (out_data, out_size)} pair and frees the native copy.
     */
    static byte[] takeBytes(MemorySegment outData, MemorySegment outSize) {
        MemorySegment data = outData.get(ADDRESS, 0);
        try {
            return data.reinterpret(KyokoDave.readSize(outSize)).toArray(JAVA_BYTE);
        } finally {
            KyokoDave.free(data);
        }
    }

    static MemorySegment allocateBytes(Arena arena, byte[] bytes) {
        return arena.allocateFrom(JAVA_BYTE, bytes);
    }

    @Override
    public abstract void close();
}
