package moe.kyokobot.libdave.ffm;

import io.netty.buffer.ByteBuf;
import moe.kyokobot.libdave.MediaType;
import moe.kyokobot.libdave.netty.NettyEncryptor;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public class FfmNettyEncryptor extends FfmEncryptor implements NettyEncryptor {
    public FfmNettyEncryptor(MemorySegment handle) {
        super(handle);
    }

    @Override
    public int encrypt(MediaType mediaType, int ssrc, ByteBuf frame, ByteBuf encryptedFrame) {
        assertOpen();
        int res;
        if (frame.hasMemoryAddress() && encryptedFrame.hasMemoryAddress()) {
            // Fast-path: direct native pointer access.
            // Account for reader index in the input frame
            MemorySegment frameSegment = MemorySegment.ofAddress(frame.memoryAddress() + frame.readerIndex());
            // Account for writer index in the output frame
            MemorySegment encryptedFrameSegment =
                    MemorySegment.ofAddress(encryptedFrame.memoryAddress() + encryptedFrame.writerIndex());

            res = KyokoDave.encryptorEncrypt(handle, mediaType.getValue(), ssrc,
                    frameSegment, frame.readableBytes(),
                    encryptedFrameSegment, encryptedFrame.writableBytes());
        } else {
            ByteBuffer frameNio = frame.nioBuffer(frame.readerIndex(), frame.readableBytes());
            ByteBuffer encryptedFrameNio = encryptedFrame.nioBuffer(encryptedFrame.writerIndex(), encryptedFrame.writableBytes());
            res = encrypt(mediaType, ssrc, frameNio, encryptedFrameNio);
        }

        if (res > 0) {
            encryptedFrame.writerIndex(encryptedFrame.writerIndex() + res);
        }
        return res;
    }
}
