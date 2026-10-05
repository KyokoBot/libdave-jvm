package moe.kyokobot.libdave;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A read-only map representing the roster of participants in a DAVE session and their associated key data.
 * <p>
 * This maps user IDs ({@link Long}) to their key material ({@code byte[]}).
 * In the context of {@link CommitResult}, this map represents changes: an entry with a value indicates an add/update,
 * while an entry might represent removal depending on context (though typically removals might be explicit or implied by absence in a full snapshot,
 * but specifically for commit results, it lists changed IDs).
 * <p>
 * Note: The implementation is backed by native arrays and is immutable.
 */
public class RosterMap implements Map<Long, byte[]> {
    private final long[] keys;
    private final byte[][] values;

    /**
     * Creates a roster map from parallel arrays. The arrays are used as-is and must not be modified afterwards.
     *
     * @param keys   The user IDs.
     * @param values The key data for each user ID, at the same index as in {@code keys}.
     * @throws IllegalArgumentException if the arrays differ in length.
     * @throws NullPointerException     if any value is null.
     */
    public RosterMap(long[] keys, byte[][] values) {
        if (keys.length != values.length) {
            throw new IllegalArgumentException("keys and values must have the same length");
        }

        for (int i = 0; i < keys.length; i++) {
            Objects.requireNonNull(values[i], "values must not be null");
        }

        this.keys = keys;
        this.values = values;
    }


    @Override
    public int size() {
        return keys.length;
    }

    @Override
    public boolean isEmpty() {
        return keys.length == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof Long)) {
            return false;
        }
        return get((long) (Long) key) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        if (!(value instanceof byte[])) {
            return false;
        }
        byte[] target = (byte[]) value;
        for (byte[] v : values) {
            if (Arrays.equals(v, target)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public byte[] get(Object key) {
        if (!(key instanceof Long)) {
            return null;
        }
        return get((long) (Long) key);
    }

    /**
     * Returns the key data for a user ID without boxing.
     *
     * @param key The user ID.
     * @return The key data, or null if the user ID is not in the roster.
     */
    public byte[] get(long key) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == key) {
                return values[i];
            }
        }
        return null;
    }

    @Override
    public byte[] put(Long key, byte[] value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(@NotNull Map<? extends Long, ? extends byte[]> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull Set<Long> keySet() {
        return Arrays.stream(keys)
                .boxed()
                .collect(Collectors.collectingAndThen(
                        Collectors.toSet(),
                        Collections::unmodifiableSet
                ));
    }

    @Override
    public @NotNull Collection<byte[]> values() {
        return Collections.unmodifiableList(Arrays.asList(values));
    }

    @Override
    public @NotNull Set<Entry<Long, byte[]>> entrySet() {
        return IntStream.range(0, keys.length)
                .mapToObj(i -> new AbstractMap.SimpleImmutableEntry<>(keys[i], values[i]))
                .collect(Collectors.collectingAndThen(
                        Collectors.toSet(),
                        Collections::unmodifiableSet
                ));
    }
}
