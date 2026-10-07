package moe.kyokobot.libdave;

/**
 * Plays the voice gateway's role in tests: it signs the external proposals that let sessions form a real MLS group.
 * <p>
 * Backed by libdave's test {@code ExternalSender}, which lives in the separate {@code dave-jvm-testing} native library
 * (see {@code natives/README.md}). The Gradle test tasks pass its location in the {@code libdave.testing.path} system
 * property.
 */
public final class TestExternalSender implements AutoCloseable {
    static {
        String path = System.getProperty("libdave.testing.path");
        if (path == null) {
            throw new IllegalStateException("libdave.testing.path is not set. Build the dave-jvm-testing CMake target "
                    + "(see natives/README.md) before running the tests.");
        }
        System.load(path);
    }

    private long handle;

    public TestExternalSender(int protocolVersion, long groupId) {
        this.handle = create(protocolVersion, groupId);
    }

    public byte[] getMarshalledExternalSender() {
        return getMarshalledExternalSender(handle);
    }

    /**
     * Returns serialized external proposals adding the owner of {@code keyPackage} to the group.
     */
    public byte[] proposeAdd(int epoch, byte[] keyPackage) {
        return proposeAdd(handle, epoch, keyPackage);
    }

    /**
     * Splits the output of {@link Session#processProposals} into {@code {commit, welcome}}.
     */
    public byte[][] splitCommitWelcome(byte[] commitWelcome) {
        return splitCommitWelcome(handle, commitWelcome);
    }

    @Override
    public void close() {
        if (handle != 0) {
            destroy(handle);
            handle = 0;
        }
    }

    private static native long create(int protocolVersion, long groupId);

    private static native void destroy(long handle);

    private static native byte[] getMarshalledExternalSender(long handle);

    private static native byte[] proposeAdd(long handle, int epoch, byte[] keyPackage);

    private static native byte[][] splitCommitWelcome(long handle, byte[] commitWelcome);
}
