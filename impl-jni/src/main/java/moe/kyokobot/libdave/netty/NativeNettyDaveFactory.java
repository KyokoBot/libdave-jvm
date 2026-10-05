package moe.kyokobot.libdave.netty;

import moe.kyokobot.libdave.Decryptor;
import moe.kyokobot.libdave.Encryptor;
import moe.kyokobot.libdave.NativeDaveFactory;
import moe.kyokobot.libdave.impl.*;

/**
 * {@link NativeDaveFactory} that can also convert its encryptors and decryptors to Netty-enabled ones.
 */
public class NativeNettyDaveFactory extends NativeDaveFactory implements NettyDaveFactory {
    /**
     * Creates a new factory. The native library is loaded on first use.
     */
    public NativeNettyDaveFactory() {
    }

    @Override
    public NettyDecryptor fromDecryptor(Decryptor decryptor) {
        if (decryptor instanceof NativeNettyDecryptor) {
            return (NativeNettyDecryptor) decryptor;
        }

        if (decryptor instanceof NativeDecryptor) {
            long handle = HandleStealer.stealHandle((NativeDecryptor) decryptor);
            return new NativeNettyDecryptor(handle);
        }

        throw new IllegalArgumentException("The passed Decryptor was not created by native Session!");
    }

    @Override
    public NettyEncryptor fromEncryptor(Encryptor encryptor) {
        if (encryptor instanceof NativeNettyEncryptor) {
            return (NativeNettyEncryptor) encryptor;
        }

        if (encryptor instanceof NativeEncryptor) {
            long handle = HandleStealer.stealHandle((NativeEncryptor) encryptor);
            return new NativeNettyEncryptor(handle);
        }

        throw new IllegalArgumentException("The passed Encryptor was not created by native Session!");
    }
}
