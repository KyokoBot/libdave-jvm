package moe.kyokobot.libdave;

/**
 * Enumeration of media types (Audio/Video).
 */
public enum MediaType {
    /** Audio frames. */
    AUDIO(0),
    /** Video frames. */
    VIDEO(1);

    private final int value;

    MediaType(int value) {
        this.value = value;
    }

    /**
     * Gets the integer value of the media type.
     *
     * @return The media type value.
     */
    public int getValue() {
        return value;
    }

    /**
     * Retrieves the MediaType enum from its integer value.
     *
     * @param value The integer value.
     * @return The corresponding {@link MediaType}.
     * @throws IllegalArgumentException if the value is unknown.
     */
    public static MediaType fromValue(int value) {
        for (MediaType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown MediaType value: " + value);
    }
}

