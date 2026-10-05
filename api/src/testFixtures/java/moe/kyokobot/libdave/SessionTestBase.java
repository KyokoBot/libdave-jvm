package moe.kyokobot.libdave;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public abstract class SessionTestBase {
    private static final byte[] GARBAGE = {1, 2, 3, 4};

    abstract DaveFactory getDaveFactory();

    private Session createSession(DaveFactory factory, List<String> failures) {
        return factory.createSession("", "auth-session", (source, reason) -> failures.add(source + ": " + reason));
    }

    @Test
    public void maxSupportedProtocolVersion() {
        assertTrue(getDaveFactory().maxSupportedProtocolVersion() >= 1);
    }

    @Test
    public void initProducesKeyPackage() {
        DaveFactory factory = getDaveFactory();
        try (Session session = createSession(factory, new CopyOnWriteArrayList<>())) {
            session.init(1, 1234L, "111");

            assertEquals(1, session.getProtocolVersion());
            assertTrue(session.getMarshalledKeyPackage().length > 0);
            assertNotNull(session.getLastEpochAuthenticator());
        }
    }

    @Test
    public void invalidMessagesAreRejected() {
        DaveFactory factory = getDaveFactory();
        List<String> failures = new CopyOnWriteArrayList<>();
        try (Session session = createSession(factory, failures)) {
            session.init(1, 1234L, "111");

            // Without any group state, libdave ignores commits rather than failing them.
            assertTrue(session.processCommit(GARBAGE).isIgnored());
            assertNull(session.processWelcome(GARBAGE, new String[]{"111", null, "222"}));
            assertNull(session.processProposals(GARBAGE, new String[]{"222"}));
            assertFalse(failures.isEmpty(), "MLS failure callback was not invoked");
        }
    }

    @Test
    public void noGroupState() throws Exception {
        DaveFactory factory = getDaveFactory();
        try (Session session = createSession(factory, new CopyOnWriteArrayList<>())) {
            session.init(1, 1234L, "111");

            assertNull(session.getKeyRatchet("222"));
            byte[] fingerprint = session.getPairwiseFingerprint(1, "222").get(5, TimeUnit.SECONDS);
            assertEquals(0, fingerprint.length);
        }
    }

    @Test
    public void protocolVersionChangedCallback() {
        DaveFactory factory = getDaveFactory();
        AtomicInteger changes = new AtomicInteger();
        try (Encryptor encryptor = factory.createEncryptor()) {
            encryptor.setProtocolVersionChangedCallback(changes::incrementAndGet);
            encryptor.setPassthroughMode(true);

            assertEquals(0, encryptor.getProtocolVersion());
            assertEquals(1, changes.get());

            encryptor.setProtocolVersionChangedCallback(null);
            encryptor.setPassthroughMode(false);
            assertEquals(1, changes.get());
        }
    }
}
