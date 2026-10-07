package moe.kyokobot.libdave;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Builds real MLS groups for tests, following libdave's own {@code mls_session_tests.cpp}.
 */
public final class TestGroup implements AutoCloseable {
    public static final int PROTOCOL_VERSION = 1;
    public static final long GROUP_ID = 1234567890L;
    public static final long OTHER_GROUP_ID = 987654321L;
    public static final String USER_A = "1234123412341234";
    public static final String USER_B = "5678567856785678";
    public static final String USER_C = "9012901290129012";

    private final DaveFactory factory;
    private final List<AutoCloseable> resources = new ArrayList<>();
    public final TestExternalSender externalSender;

    public TestGroup(DaveFactory factory) {
        this.factory = factory;
        this.externalSender = new TestExternalSender(PROTOCOL_VERSION, GROUP_ID);
        resources.add(externalSender);
    }

    /**
     * A session that records the reasons passed to its MLS failure callback.
     */
    public static final class Member {
        public final String userId;
        public final Session session;
        public final List<String> failures = new CopyOnWriteArrayList<>();

        private Member(DaveFactory factory, String userId) {
            this.userId = userId;
            this.session = factory.createSession("", "", (source, reason) -> failures.add(reason));
        }
    }

    /**
     * Creates a session that has not been initialized.
     */
    public Member member(String userId) {
        Member member = new Member(factory, userId);
        resources.add(member.session);
        return member;
    }

    /**
     * Creates a session that is initialized for {@code groupId} and knows the external sender.
     */
    public Member initializedMember(String userId, long groupId) {
        Member member = member(userId);
        member.session.init(PROTOCOL_VERSION, groupId, userId);
        member.session.setExternalSender(externalSender.getMarshalledExternalSender());
        return member;
    }

    /**
     * Has the external sender propose adding {@code joiner}, and returns the {@code {commit, welcome}} that
     * {@code creator} produces for it.
     */
    public byte[][] proposeAddAndCommit(Member creator, Member joiner) {
        byte[] proposals = externalSender.proposeAdd(0, joiner.session.getMarshalledKeyPackage());
        byte[] commitWelcome = creator.session.processProposals(proposals,
                new String[]{creator.userId, joiner.userId});
        assertNotNull(commitWelcome, "creator did not produce a commit");
        return externalSender.splitCommitWelcome(commitWelcome);
    }

    /**
     * Forms a group of {@link #USER_A} and {@link #USER_B}, returning {@code {a, b}}.
     */
    public Member[] establish() {
        Member a = initializedMember(USER_A, GROUP_ID);
        Member b = initializedMember(USER_B, GROUP_ID);
        byte[][] commitWelcome = proposeAddAndCommit(a, b);

        CommitResult commitResult = a.session.processCommit(commitWelcome[0]);
        assertEquals(2, commitResult.getRosterMap().size());
        RosterMap welcomeRoster = b.session.processWelcome(commitWelcome[1], new String[]{USER_A, USER_B});
        assertNotNull(welcomeRoster, "welcome was rejected: " + b.failures);
        return new Member[]{a, b};
    }

    @Override
    public void close() throws Exception {
        for (int i = resources.size() - 1; i >= 0; i--) {
            resources.get(i).close();
        }
    }
}
