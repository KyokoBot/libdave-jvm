package moe.kyokobot.libdave;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class ValueTypesTest {
    @Test
    public void enumValuesRoundTrip() {
        for (Codec codec : Codec.values()) {
            assertSame(codec, Codec.fromValue(codec.getValue()));
        }
        for (MediaType mediaType : MediaType.values()) {
            assertSame(mediaType, MediaType.fromValue(mediaType.getValue()));
        }
        for (EncryptorResultCode code : EncryptorResultCode.values()) {
            assertSame(code, EncryptorResultCode.fromValue(code.getValue()));
        }
        for (DecryptorResultCode code : DecryptorResultCode.values()) {
            assertSame(code, DecryptorResultCode.fromValue(code.getValue()));
        }
    }

    @Test
    public void unknownEnumValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Codec.fromValue(-1));
        assertThrows(IllegalArgumentException.class, () -> Codec.fromValue(Codec.values().length));
        assertThrows(IllegalArgumentException.class, () -> MediaType.fromValue(2));
        assertThrows(IllegalArgumentException.class, () -> EncryptorResultCode.fromValue(-1));
        assertThrows(IllegalArgumentException.class, () -> DecryptorResultCode.fromValue(5));
    }

    @Test
    public void rosterMapLookups() {
        byte[] keyA = {1, 2, 3};
        byte[] removed = {};
        RosterMap roster = new RosterMap(new long[]{10L, -1L}, new byte[][]{keyA, removed});

        assertEquals(2, roster.size());
        assertFalse(roster.isEmpty());
        assertSame(keyA, roster.get(10L));
        assertSame(keyA, roster.get((Object) 10L));
        assertSame(removed, roster.get(-1L));
        assertNull(roster.get(11L));
        assertNull(roster.get("10"));
        assertTrue(roster.containsKey(-1L));
        assertFalse(roster.containsKey(10));
        assertTrue(roster.containsValue(new byte[]{1, 2, 3}));
        assertFalse(roster.containsValue(new byte[]{1, 2}));
        assertEquals(2, roster.keySet().size());
        assertEquals(2, roster.entrySet().size());
        assertTrue(new RosterMap(new long[0], new byte[0][]).isEmpty());
    }

    @Test
    public void rosterMapRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> new RosterMap(new long[]{1L}, new byte[0][]));
        assertThrows(NullPointerException.class, () -> new RosterMap(new long[]{1L}, new byte[][]{null}));
    }

    @Test
    public void rosterMapIsReadOnly() {
        RosterMap roster = new RosterMap(new long[]{1L}, new byte[][]{{1}});

        assertThrows(UnsupportedOperationException.class, () -> roster.put(2L, new byte[0]));
        assertThrows(UnsupportedOperationException.class, () -> roster.remove(1L));
        assertThrows(UnsupportedOperationException.class, () -> roster.putAll(Collections.emptyMap()));
        assertThrows(UnsupportedOperationException.class, roster::clear);
        assertThrows(UnsupportedOperationException.class, () -> roster.keySet().clear());
        assertThrows(UnsupportedOperationException.class, () -> roster.values().clear());
        assertThrows(UnsupportedOperationException.class, () -> roster.entrySet().clear());
    }

    @Test
    public void commitResults() {
        assertTrue(CommitResult.failed().isFailed());
        assertFalse(CommitResult.failed().isIgnored());
        assertThrows(IllegalStateException.class, () -> CommitResult.failed().getRosterMap());

        assertTrue(CommitResult.ignored().isIgnored());
        assertFalse(CommitResult.ignored().isFailed());
        assertThrows(IllegalStateException.class, () -> CommitResult.ignored().getRosterMap());

        RosterMap roster = new RosterMap(new long[0], new byte[0][]);
        CommitResult success = CommitResult.success(roster);
        assertFalse(success.isFailed());
        assertFalse(success.isIgnored());
        assertSame(roster, success.getRosterMap());
        assertThrows(NullPointerException.class, () -> CommitResult.success(null));
    }
}
