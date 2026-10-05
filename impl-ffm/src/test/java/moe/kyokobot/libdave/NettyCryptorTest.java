package moe.kyokobot.libdave;

import moe.kyokobot.libdave.netty.FfmNettyDaveFactory;
import moe.kyokobot.libdave.netty.NettyDaveFactory;

public class NettyCryptorTest extends NettyCryptorTestBase {
    @Override
    NettyDaveFactory getDaveFactory() {
        FfmDaveFactory.ensureAvailable();
        return new FfmNettyDaveFactory();
    }
}
