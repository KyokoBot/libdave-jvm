package moe.kyokobot.libdave.jda;

import moe.kyokobot.libdave.DaveFactory;
import net.dv8tion.jda.api.audio.dave.DaveProtocolCallbacks;
import net.dv8tion.jda.api.audio.dave.DaveSession;
import net.dv8tion.jda.api.audio.dave.DaveSessionFactory;
import org.jetbrains.annotations.NotNull;

/**
 * JDA {@link DaveSessionFactory} that creates DAVE sessions backed by libdave-jvm.
 */
public class LDJDADaveSessionFactory implements DaveSessionFactory {
    private final DaveFactory factory;

    /**
     * Creates a session factory.
     *
     * @param factory The libdave-jvm factory used to create the underlying sessions, encryptors and decryptors.
     */
    public LDJDADaveSessionFactory(DaveFactory factory) {
        this.factory = factory;
    }

    @Override
    @NotNull
    public DaveSession createDaveSession(@NotNull DaveProtocolCallbacks callbacks, long userId, long channelId) {
        return new LDJDADaveSession(factory, userId, channelId, callbacks);
    }
}
