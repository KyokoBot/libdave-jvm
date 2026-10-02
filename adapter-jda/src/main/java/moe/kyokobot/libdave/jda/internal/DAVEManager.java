package moe.kyokobot.libdave.jda.internal;

import moe.kyokobot.libdave.*;
import net.dv8tion.jda.api.audio.dave.DaveProtocolCallbacks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;

public class DAVEManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(DAVEManager.class);

    private static final long MLS_NEW_GROUP_EPOCH = 1L;
    private static final int INIT_TRANSITION_ID = 0;

    private final DaveFactory factory;
    private final Session daveSession;
    private final DaveProtocolCallbacks callbacks;
    private final long channelId;
    private final long selfUserId;
    private final String selfUserIdString;

    private final StampedLock sessionLock = new StampedLock();
    private volatile boolean closed = false;

    private final Encryptor selfEncryptor;
    private final Map<Long, Decryptor> decryptors = new ConcurrentHashMap<>();
    private final Set<Long> recognizedUserIds = ConcurrentHashMap.newKeySet();
    private final Map<Long, byte[]> activeE2EEUsers = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> pendingTransitions = new ConcurrentHashMap<>();
    private final int maxProtocolVersion;

    private @Nullable KeyRatchet selfKeyRatchet = null;
    private int currentProtocolVersion = 0;

    public DAVEManager(DaveFactory factory, long selfUserId, long channelId, DaveProtocolCallbacks callbacks) {
        this.factory = factory;
        this.selfUserId = selfUserId;
        this.selfUserIdString = Long.toUnsignedString(selfUserId);
        this.channelId = channelId;
        this.callbacks = callbacks;
        this.daveSession = factory.createSession("", "", this::mlsFailureCallback);
        this.selfEncryptor = factory.createEncryptor();
        this.selfEncryptor.setPassthroughMode(true);
        this.maxProtocolVersion = factory.maxSupportedProtocolVersion();
    }

    public int getMaxDAVEProtocolVersion() {
        return maxProtocolVersion;
    }

    public void assignSsrcToCodec(int ssrc, Codec codec) {
        long stamp = sessionLock.writeLock();
        try {
            if (closed) return;
            selfEncryptor.assignSsrcToCodec(ssrc, codec);
        } finally {
            sessionLock.unlockWrite(stamp);
        }
    }

    public int getMaxCiphertextByteSize(MediaType mediaType, int frameSize) {
        long stamp = sessionLock.readLock();
        try {
            if (closed) return frameSize;
            return selfEncryptor.getMaxCiphertextByteSize(mediaType, frameSize);
        } finally {
            sessionLock.unlockRead(stamp);
        }
    }

    public int getMaxPlaintextByteSize(MediaType mediaType, long userId, int frameSize) {
        long stamp = sessionLock.readLock();
        try {
            if (closed) return frameSize;
            Decryptor decryptor = decryptors.get(userId);
            if (decryptor == null) {
                return frameSize;
            }
            return decryptor.getMaxPlaintextByteSize(mediaType, frameSize);
        } finally {
            sessionLock.unlockRead(stamp);
        }
    }

    public void addUser(long userId) {
        long stamp = sessionLock.writeLock();
        try {
            if (closed) return;
            logger.debug("Adding user {}", userId);
            recognizedUserIds.add(userId);

            // Initialize decryptor for the user immediately if not present
            Decryptor decryptor = decryptors.computeIfAbsent(userId, id -> factory.createDecryptor());
            decryptor.transitionToPassthroughMode(currentProtocolVersion == 0);
        } finally {
            sessionLock.unlockWrite(stamp);
        }
    }

    public void removeUser(long userId) {
        long stamp = sessionLock.writeLock();
        try {
            if (closed) return;
            logger.debug("Removing user {}", userId);
            recognizedUserIds.remove(userId);
            activeE2EEUsers.remove(userId);
            Decryptor decryptor = decryptors.remove(userId);
            if (decryptor != null) {
                decryptor.close();
            }
        } finally {
            sessionLock.unlockWrite(stamp);
        }
    }

    public boolean encrypt(MediaType mediaType, int ssrc, ByteBuffer input, ByteBuffer output) {
        long stamp = sessionLock.readLock();
        try {
            if (closed) return false;
            int result = this.selfEncryptor.encrypt(mediaType, ssrc, input, output);
            boolean success = result >= 0;
            if (success) {
                output.limit(output.position() + result);
            }
            return success;
        } finally {
            sessionLock.unlockRead(stamp);
        }
    }

    public boolean decrypt(MediaType mediaType, long userId, ByteBuffer input, ByteBuffer output) {
        long stamp = sessionLock.readLock();
        try {
            if (closed) return false;
            Decryptor decryptor = decryptors.get(userId);
            if (decryptor == null) return false;

            int result = decryptor.decrypt(mediaType, input, output);
            boolean success = result >= 0;
            if (success) {
                output.limit(output.position() + result);
            }
            return success;
        } finally {
            sessionLock.unlockRead(stamp);
        }
    }

    public void onSelectProtocolAck(int protocolVersion) {
        long stamp = sessionLock.writeLock();
        try {
            if (closed) return;
            logger.debug("Handle select protocol version {}", protocolVersion);
            daveProtocolInit(protocolVersion);
        } finally {
            sessionLock.unlockWrite(stamp);
        }
    }

    public void onDaveProtocolPrepareTransition(int transitionId, int protocolVersion) {
        long stamp = sessionLock.writeLock();
        try {
            if (closed) return;
            logger.debug("Preparing transition to protocol version={} (ID {})", protocolVersion, transitionId);
            prepareRatchets(transitionId, protocolVersion);
            if (transitionId != INIT_TRANSITION_ID) {
                callbacks.sendDaveProtocolReadyForTransition(transitionId);
            }
        } finally {
            sessionLock.unlockWrite(stamp);
        }
    }

    public void onDaveProtocolExecuteTransition(int transitionId) {
        long stamp = sessionLock.writeLock();
        try {
            if (closed) return;
            logger.debug("Executing transition (ID {})", transitionId);
            executeTransition(transitionId);
        } finally {
            sessionLock.unlockWrite(stamp);
        }
    }

    public void onDaveProtocolPrepareEpoch(long epoch, int protocolVersion) {
        long stamp = sessionLock.writeLock();
        try {
            if (closed) return;
            logger.debug("Preparing epoch {} (protocol version {})", epoch, protocolVersion);
            prepareEpoch(epoch, protocolVersion);

            if (epoch == MLS_NEW_GROUP_EPOCH) {
                sendMLSKeyPackage();
            }
        } finally {
            sessionLock.unlockWrite(stamp);
        }
    }

    public void onDaveProtocolMLSExternalSenderPackage(ByteBuffer externalSenderPackage) {
        long stamp = sessionLock.writeLock();
        try {
            if (closed) return;
            byte[] bytes = new byte[externalSenderPackage.remaining()];
            externalSenderPackage.get(bytes);
            daveSession.setExternalSender(bytes);
        } finally {
            sessionLock.unlockWrite(stamp);
        }
    }

    public void onMLSProposals(ByteBuffer proposals) {
        long stamp = sessionLock.writeLock();
        try {
            if (closed) return;
            byte[] bytes = new byte[proposals.remaining()];
            proposals.get(bytes);
            byte[] commitWelcome = daveSession.processProposals(bytes, recognizedUserIdArray());
            if (commitWelcome != null && commitWelcome.length > 0) {
                callbacks.sendMLSCommitWelcome(ByteBuffer.wrap(commitWelcome));
            }
        } finally {
            sessionLock.unlockWrite(stamp);
        }
    }

    public void onMLSPrepareCommitTransition(int transitionId, ByteBuffer commit) {
        long stamp = sessionLock.writeLock();
        try {
            if (closed) return;
            byte[] bytes = new byte[commit.remaining()];
            commit.get(bytes);
            CommitResult result = daveSession.processCommit(bytes);

            if (result.isIgnored()) {
                pendingTransitions.remove(transitionId);
                return;
            }

            if (result.isFailed()) {
                callbacks.sendMLSInvalidCommitWelcome(transitionId);
                daveProtocolInit(daveSession.getProtocolVersion());
                return;
            }

            updateActiveUsers(result.getRosterMap());

            prepareRatchets(transitionId, daveSession.getProtocolVersion());
            if (transitionId != INIT_TRANSITION_ID) {
                callbacks.sendDaveProtocolReadyForTransition(transitionId);
            }
        } finally {
            sessionLock.unlockWrite(stamp);
        }
    }

    public void onMLSWelcome(int transitionId, ByteBuffer welcome) {
        long stamp = sessionLock.writeLock();
        try {
            if (closed) return;
            byte[] bytes = new byte[welcome.remaining()];
            welcome.get(bytes);
            RosterMap roster = daveSession.processWelcome(bytes, recognizedUserIdArray());

            if (roster == null) {
                callbacks.sendMLSInvalidCommitWelcome(transitionId);
                sendMLSKeyPackage();
                return;
            }

            updateActiveUsers(roster);

            prepareRatchets(transitionId, daveSession.getProtocolVersion());
            if (transitionId != INIT_TRANSITION_ID) {
                callbacks.sendDaveProtocolReadyForTransition(transitionId);
            }
        } finally {
            sessionLock.unlockWrite(stamp);
        }
    }

    private void daveProtocolInit(int protocolVersion) {
        logger.debug("DAVE Init - Protocol version={}", protocolVersion);
        if (protocolVersion > 0) {
            prepareEpoch(MLS_NEW_GROUP_EPOCH, protocolVersion);
            sendMLSKeyPackage();
        } else {
            activeE2EEUsers.clear();
            prepareRatchets(INIT_TRANSITION_ID, protocolVersion);
            executeTransition(INIT_TRANSITION_ID);
        }
    }

    private void updateActiveUsers(RosterMap roster) {
        for (Map.Entry<Long, byte[]> entry : roster.entrySet()) {
            Long userId = entry.getKey();
            byte[] key = entry.getValue();
            if (key == null || key.length == 0) {
                activeE2EEUsers.remove(userId);
            } else {
                activeE2EEUsers.put(userId, key);
            }
        }
    }

    private void prepareEpoch(long epoch, int protocolVersion) {
        if (closed) return;
        if (epoch == MLS_NEW_GROUP_EPOCH) {
            daveSession.init(protocolVersion, channelId, selfUserIdString);
        }
    }

    private void setupKeyRatchetForUser(String uid, int protocolVersion) {
        KeyRatchet keyRatchet = makeKeyRatchetForUser(uid, protocolVersion);
        if (selfUserIdString.equals(uid)) {
            setSelfKeyRatchet(keyRatchet);
        } else {
            Long userId = Long.parseUnsignedLong(uid);

            Decryptor decryptor = decryptors.computeIfAbsent(userId, id -> factory.createDecryptor());
            if (keyRatchet != null) {
                decryptor.transitionToKeyRatchet(keyRatchet);
            } else {
                decryptor.transitionToPassthroughMode(true);
            }
        }
    }

    @Nullable
    private KeyRatchet makeKeyRatchetForUser(String uid, int protocolVersion) {
        if (protocolVersion == 0) {
            return null;
        }

        return daveSession.getKeyRatchet(uid);
    }

    private void prepareRatchets(int transitionId, int protocolVersion) {
        if (protocolVersion == 0) {
            for (Long userId : recognizedUserIds) {
                setupKeyRatchetForUser(Long.toUnsignedString(userId), 0);
            }
            setupKeyRatchetForUser(selfUserIdString, 0);
        } else {
            // Setup ratchets for all users JDA has told us about that are in the MLS group
            for (Long userId : recognizedUserIds) {
                if (selfUserId == userId) continue;

                if (activeE2EEUsers.containsKey(userId)) {
                    setupKeyRatchetForUser(Long.toUnsignedString(userId), protocolVersion);
                } else {
                    Decryptor decryptor = decryptors.get(userId);
                    if (decryptor != null) {
                        decryptor.transitionToPassthroughMode(true);
                    }
                }
            }
        }

        if (transitionId == INIT_TRANSITION_ID) {
            setupKeyRatchetForUser(selfUserIdString, protocolVersion);
        } else {
            pendingTransitions.put(transitionId, protocolVersion);
        }

        currentProtocolVersion = protocolVersion;
    }

    private void executeTransition(int transitionId) {
        Integer protocolVersion = pendingTransitions.remove(transitionId);
        if (protocolVersion == null && transitionId != INIT_TRANSITION_ID) {
            return;
        }

        int version = protocolVersion != null ? protocolVersion : currentProtocolVersion;

        if (version == 0) {
            daveSession.reset();
            activeE2EEUsers.clear();
        }

        setupKeyRatchetForUser(selfUserIdString, version);
        logger.debug("Transition executed: ID={}, Protocol version={}", transitionId, version);
    }

    private void setSelfKeyRatchet(@Nullable KeyRatchet selfKeyRatchet) {
        if (this.selfKeyRatchet != null) {
            this.selfKeyRatchet.close();
        }

        this.selfKeyRatchet = selfKeyRatchet;

        if (this.selfKeyRatchet == null) {
            this.selfEncryptor.setPassthroughMode(true);
        } else {
            this.selfEncryptor.setKeyRatchet(selfKeyRatchet);
            this.selfEncryptor.setPassthroughMode(false);
        }
    }

    private void sendMLSKeyPackage() {
        byte[] keyPackage = daveSession.getMarshalledKeyPackage();
        if (keyPackage != null && keyPackage.length > 0) {
            callbacks.sendMLSKeyPackage(ByteBuffer.wrap(keyPackage));
        }
    }

    private String[] recognizedUserIdArray() {
        String[] userIds = new String[recognizedUserIds.size() + 1];
        int i = 0;
        for (Long uid : recognizedUserIds) {
            userIds[i++] = Long.toUnsignedString(uid);
        }
        userIds[i] = selfUserIdString;

        return userIds;
    }

    private void mlsFailureCallback(String source, String reason) {
        logger.warn("MLS Failure - Source: {}, Reason: {}", source, reason);
    }

    @Override
    public void close() {
        closed = true;
        long stamp = sessionLock.writeLock();
        try {
            daveSession.close();
            if (selfKeyRatchet != null) {
                selfKeyRatchet.close();
                selfKeyRatchet = null;
            }
            selfEncryptor.close();
            decryptors.values().forEach(Decryptor::close);
            decryptors.clear();
        } finally {
            sessionLock.unlockWrite(stamp);
        }
    }
}
