package moe.kyokobot.libdave;

import moe.kyokobot.libdave.callbacks.DaveLogSink;

/**
 * Leaves native objects, callbacks and a log sink alive and then lets the JVM exit, which must not crash.
 * Run by {@link ProcessExitTestBase} in a separate JVM.
 * <p>
 * Arguments: the {@link DaveFactory} class name, then {@code exit} to call {@link System#exit} or {@code return} to
 * return from {@code main}.
 */
public final class ExitWithLiveNativeState {
    public static final String READY = "native state is set up";

    private ExitWithLiveNativeState() {
    }

    public static void main(String[] args) throws Exception {
        Class<?> factoryClass = Class.forName(args[0]);
        DaveLogSink sink = (severity, file, line, message) -> {
        };
        factoryClass.getMethod("setLogSink", DaveLogSink.class).invoke(null, sink);
        DaveFactory factory = (DaveFactory) factoryClass.getConstructor().newInstance();

        Session session = factory.createSession("", "", (source, reason) -> {
        });
        session.init(1, 1234L, "111");
        session.processCommit(new byte[]{1, 2, 3, 4});
        Encryptor encryptor = factory.createEncryptor();
        encryptor.setProtocolVersionChangedCallback(() -> {
        });
        encryptor.setPassthroughMode(true);

        System.out.println(READY);
        System.out.flush();
        if ("exit".equals(args[1])) {
            System.exit(0);
        }
    }
}
