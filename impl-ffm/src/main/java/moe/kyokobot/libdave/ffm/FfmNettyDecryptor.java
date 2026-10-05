package moe.kyokobot.libdave.ffm;

import io.netty.buffer.ByteBuf;
import moe.kyokobot.libdave.MediaType;
import moe.kyokobot.libdave.netty.NettyDecryptor;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public class FfmNettyDecryptor extends FfmDecryptor implements NettyDecryptor {
    public FfmNettyDecryptor(MemorySegment handle) {
        super(handle);
    }

    @Override
    public int decrypt(MediaType mediaType, ByteBuf encryptedFrame, ByteBuf frame) {
        assertOpen();
        int res;
        if (encryptedFrame.hasMemoryAddress() && frame.hasMemoryAddress()) {
            // Fast-path: direct native pointer access.
            // Account for reader index in the input encrypted frame
            MemorySegment encryptedFrameSegment =
                    MemorySegment.ofAddress(encryptedFrame.memoryAddress() + encryptedFrame.readerIndex());
            // Account for writer index in the output frame
            MemorySegment frameSegment = MemorySegment.ofAddress(frame.memoryAddress() + frame.writerIndex());

            res = KyokoDave.decryptorDecrypt(handle, mediaType.getValue(),
                    encryptedFrameSegment, encryptedFrame.readableBytes(),
                    frameSegment, frame.writableBytes());
        } else {
            ByteBuffer encryptedFrameNio = encryptedFrame.nioBuffer(encryptedFrame.readerIndex(), encryptedFrame.readableBytes());
            ByteBuffer frameNio = frame.nioBuffer(frame.writerIndex(), frame.writableBytes());
            res = decrypt(mediaType, encryptedFrameNio, frameNio);
        }

        if (res > 0) {
            frame.writerIndex(frame.writerIndex() + res);
        }
        return res;
    }
}
