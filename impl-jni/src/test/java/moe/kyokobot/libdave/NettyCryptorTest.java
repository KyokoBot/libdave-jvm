package moe.kyokobot.libdave;

import moe.kyokobot.libdave.netty.NativeNettyDaveFactory;
import moe.kyokobot.libdave.netty.NettyDaveFactory;

public class NettyCryptorTest extends NettyCryptorTestBase {
    @Override
    NettyDaveFactory getDaveFactory() {
        NativeDaveFactory.ensureAvailable();
        return new NativeNettyDaveFactory();
    }
}
