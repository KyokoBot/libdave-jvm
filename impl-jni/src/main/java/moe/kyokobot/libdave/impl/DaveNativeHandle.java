package moe.kyokobot.libdave.impl;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public abstract class DaveNativeHandle implements AutoCloseable {
    protected final long handle;
    protected volatile boolean closed;

    protected DaveNativeHandle(long handle) {
        if (handle == 0) {
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

    long getHandle() {
        return handle;
    }

    long stealHandle() {
        assertOpen();
        this.closed = true;
        return handle;
    }

    @Override
    public abstract void close() throws Exception;
}
