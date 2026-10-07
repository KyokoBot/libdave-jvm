package moe.kyokobot.libdave;

import moe.kyokobot.libdave.TestGroup.Member;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static moe.kyokobot.libdave.TestGroup.*;
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

    @Test
    public void joinViaCommitAndWelcome() throws Exception {
        try (TestGroup group = new TestGroup(getDaveFactory())) {
            Member a = group.initializedMember(USER_A, GROUP_ID);
            Member b = group.initializedMember(USER_B, GROUP_ID);
            byte[][] commitWelcome = group.proposeAddAndCommit(a, b);

            CommitResult commitResult = a.session.processCommit(commitWelcome[0]);
            assertFalse(commitResult.isFailed());
            assertFalse(commitResult.isIgnored());
            assertRoster(commitResult.getRosterMap());

            RosterMap welcomeRoster = b.session.processWelcome(commitWelcome[1], new String[]{USER_A, USER_B});
            assertNotNull(welcomeRoster);
            assertRoster(welcomeRoster);

            byte[] authenticator = a.session.getLastEpochAuthenticator();
            assertTrue(authenticator.length > 0);
            assertArrayEquals(authenticator, b.session.getLastEpochAuthenticator());
            assertTrue(a.failures.isEmpty(), a.failures::toString);
            assertTrue(b.failures.isEmpty(), b.failures::toString);
        }
    }

    private static void assertRoster(RosterMap roster) {
        assertEquals(2, roster.size());
        assertTrue(roster.get(Long.parseUnsignedLong(USER_A)).length > 0);
        assertTrue(roster.get(Long.parseUnsignedLong(USER_B)).length > 0);
    }

    @Test
    public void pairwiseFingerprintsMatch() throws Exception {
        try (TestGroup group = new TestGroup(getDaveFactory())) {
            Member[] members = group.establish();

            byte[] fingerprintA = members[0].session.getPairwiseFingerprint(1, USER_B).get(30, TimeUnit.SECONDS);
            byte[] fingerprintB = members[1].session.getPairwiseFingerprint(1, USER_A).get(30, TimeUnit.SECONDS);
            assertEquals(64, fingerprintA.length);
            assertArrayEquals(fingerprintA, fingerprintB);

            byte[] unknown = members[0].session.getPairwiseFingerprint(1, USER_C).get(30, TimeUnit.SECONDS);
            assertEquals(0, unknown.length);
        }
    }

    @Test
    public void welcomeRejectsUnrecognizedRosterUser() throws Exception {
        try (TestGroup group = new TestGroup(getDaveFactory())) {
            Member a = group.initializedMember(USER_A, GROUP_ID);
            Member b = group.initializedMember(USER_B, GROUP_ID);
            byte[][] commitWelcome = group.proposeAddAndCommit(a, b);

            assertNull(b.session.processWelcome(commitWelcome[1], new String[]{USER_B}));
            assertTrue(b.failures.contains("Welcome message lists unrecognized user ID"), b.failures::toString);
            assertNull(b.session.getKeyRatchet(USER_A));
        }
    }

    @Test
    public void welcomeRejectsMismatchedGroupId() throws Exception {
        try (TestGroup group = new TestGroup(getDaveFactory())) {
            Member a = group.initializedMember(USER_A, GROUP_ID);
            Member b = group.initializedMember(USER_B, OTHER_GROUP_ID);
            byte[][] commitWelcome = group.proposeAddAndCommit(a, b);

            assertNull(b.session.processWelcome(commitWelcome[1], new String[]{USER_A, USER_B}));
            assertTrue(b.failures.contains("Unexpected group ID in Welcome"), b.failures::toString);
        }
    }

    @Test
    public void commitBeforeWelcomeFails() throws Exception {
        try (TestGroup group = new TestGroup(getDaveFactory())) {
            Member a = group.initializedMember(USER_A, GROUP_ID);
            Member b = group.initializedMember(USER_B, GROUP_ID);
            Member c = group.initializedMember(USER_C, GROUP_ID);
            byte[][] commitWelcomeA = group.proposeAddAndCommit(a, b);
            group.proposeAddAndCommit(b, c);

            assertTrue(b.session.processCommit(commitWelcomeA[0]).isFailed());
            assertTrue(b.failures.contains("Unexpected commit before welcome"), b.failures::toString);
        }
    }

    @Test
    public void replayedCommitFails() throws Exception {
        try (TestGroup group = new TestGroup(getDaveFactory())) {
            Member a = group.initializedMember(USER_A, GROUP_ID);
            Member b = group.initializedMember(USER_B, GROUP_ID);
            byte[][] commitWelcome = group.proposeAddAndCommit(a, b);
            assertFalse(a.session.processCommit(commitWelcome[0]).isFailed());

            CommitResult replay = a.session.processCommit(commitWelcome[0]);
            assertTrue(replay.isFailed());
            assertThrows(IllegalStateException.class, replay::getRosterMap);
            assertTrue(a.failures.contains("ProcessCommit called without queued proposals"), a.failures::toString);
        }
    }

    @Test
    public void garbageCommitInEstablishedGroupFails() throws Exception {
        try (TestGroup group = new TestGroup(getDaveFactory())) {
            Member a = group.establish()[0];

            assertTrue(a.session.processCommit(GARBAGE).isFailed());
            assertFalse(a.failures.isEmpty());
            // A rejected commit must not disturb the established group.
            assertNotNull(a.session.getKeyRatchet(USER_B));
        }
    }

    @Test
    public void setExternalSenderAfterJoinReportsFailure() throws Exception {
        try (TestGroup group = new TestGroup(getDaveFactory())) {
            Member a = group.establish()[0];

            a.session.setExternalSender(group.externalSender.getMarshalledExternalSender());
            assertTrue(a.failures.contains("Cannot set external sender after joining/creating an MLS group"),
                    a.failures::toString);
        }
    }

    @Test
    public void keyPackageWithoutInitIsEmpty() throws Exception {
        try (TestGroup group = new TestGroup(getDaveFactory())) {
            Member a = group.member(USER_A);

            assertEquals(0, a.session.getMarshalledKeyPackage().length);
            assertTrue(a.failures.contains("Missing leaf node"), a.failures::toString);
        }
    }

    @Test
    public void proposalsWithoutStateAreRejected() throws Exception {
        try (TestGroup group = new TestGroup(getDaveFactory())) {
            Member a = group.member(USER_A);

            assertNull(a.session.processProposals(new byte[0], new String[0]));
            assertTrue(a.failures.contains("Got proposal without MLS state"), a.failures::toString);
        }
    }

    @Test
    public void resetLeavesGroup() throws Exception {
        try (TestGroup group = new TestGroup(getDaveFactory())) {
            Member a = group.establish()[0];
            assertNotNull(a.session.getKeyRatchet(USER_A));

            a.session.reset();
            assertNull(a.session.getKeyRatchet(USER_A));
            assertEquals(0, a.session.getPairwiseFingerprint(1, USER_B).get(30, TimeUnit.SECONDS).length);
        }
    }

    @Test
    public void nullCallbackAndAuthSessionIdAreAllowed() {
        try (Session session = getDaveFactory().createSession(null, null, null)) {
            session.init(1, 1234L, "111");
            // Failures must be safe to report without a callback.
            assertTrue(session.processCommit(GARBAGE).isIgnored());
            assertTrue(session.getMarshalledKeyPackage().length > 0);
        }
    }

    @Test
    public void throwingFailureCallbackDoesNotPropagate() {
        AtomicInteger calls = new AtomicInteger();
        try (Session session = getDaveFactory().createSession("", "", (source, reason) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("thrown from the failure callback on purpose");
        })) {
            session.init(1, 1234L, "111");

            assertNull(session.processProposals(GARBAGE, new String[]{"222"}));
            assertTrue(calls.get() > 0);
            assertTrue(session.getMarshalledKeyPackage().length > 0);
        }
    }

    @Test
    public void nullArgumentsAreRejected() {
        try (Session session = getDaveFactory().createSession("", "", null)) {
            assertThrows(NullPointerException.class, () -> session.init(1, 1234L, null));
            session.init(1, 1234L, "111");

            assertThrows(NullPointerException.class, () -> session.setExternalSender(null));
            assertThrows(NullPointerException.class, () -> session.processProposals(null, new String[0]));
            assertThrows(NullPointerException.class, () -> session.processProposals(GARBAGE, null));
            assertThrows(NullPointerException.class, () -> session.processCommit(null));
            assertThrows(NullPointerException.class, () -> session.processWelcome(null, new String[0]));
            assertThrows(NullPointerException.class, () -> session.processWelcome(GARBAGE, null));
            assertThrows(NullPointerException.class, () -> session.getKeyRatchet(null));
            assertThrows(NullPointerException.class, () -> session.getPairwiseFingerprint(1, null));

            // The session is still usable afterwards.
            assertTrue(session.getMarshalledKeyPackage().length > 0);
        }
    }

    @Test
    public void closedSessionRejectsCalls() {
        Session session = getDaveFactory().createSession("", "", null);
        session.close();
        session.close();

        assertThrows(IllegalStateException.class, () -> session.init(1, 1234L, "111"));
        assertThrows(IllegalStateException.class, session::reset);
        assertThrows(IllegalStateException.class, () -> session.setProtocolVersion(1));
        assertThrows(IllegalStateException.class, session::getProtocolVersion);
        assertThrows(IllegalStateException.class, session::getLastEpochAuthenticator);
        assertThrows(IllegalStateException.class, () -> session.setExternalSender(GARBAGE));
        assertThrows(IllegalStateException.class, () -> session.processProposals(GARBAGE, new String[0]));
        assertThrows(IllegalStateException.class, () -> session.processCommit(GARBAGE));
        assertThrows(IllegalStateException.class, () -> session.processWelcome(GARBAGE, new String[0]));
        assertThrows(IllegalStateException.class, session::getMarshalledKeyPackage);
        assertThrows(IllegalStateException.class, () -> session.getKeyRatchet("111"));
        assertThrows(IllegalStateException.class, () -> session.getPairwiseFingerprint(1, "111"));
    }
}
