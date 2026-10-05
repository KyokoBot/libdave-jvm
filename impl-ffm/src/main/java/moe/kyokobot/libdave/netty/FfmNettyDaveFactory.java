package moe.kyokobot.libdave.netty;

import moe.kyokobot.libdave.Decryptor;
import moe.kyokobot.libdave.Encryptor;
import moe.kyokobot.libdave.FfmDaveFactory;
import moe.kyokobot.libdave.ffm.FfmDecryptor;
import moe.kyokobot.libdave.ffm.FfmEncryptor;
import moe.kyokobot.libdave.ffm.FfmNettyDecryptor;
import moe.kyokobot.libdave.ffm.FfmNettyEncryptor;

public class FfmNettyDaveFactory extends FfmDaveFactory implements NettyDaveFactory {
    @Override
    public NettyDecryptor fromDecryptor(Decryptor decryptor) {
        if (decryptor instanceof FfmNettyDecryptor nettyDecryptor) {
            return nettyDecryptor;
        }

        if (decryptor instanceof FfmDecryptor ffmDecryptor) {
            return new FfmNettyDecryptor(ffmDecryptor.stealHandle());
        }

        throw new IllegalArgumentException("The passed Decryptor was not created by FFM Session!");
    }

    @Override
    public NettyEncryptor fromEncryptor(Encryptor encryptor) {
        if (encryptor instanceof FfmNettyEncryptor nettyEncryptor) {
            return nettyEncryptor;
        }

        if (encryptor instanceof FfmEncryptor ffmEncryptor) {
            return new FfmNettyEncryptor(ffmEncryptor.stealHandle());
        }

        throw new IllegalArgumentException("The passed Encryptor was not created by FFM Session!");
    }
}
