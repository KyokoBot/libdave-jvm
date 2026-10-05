package moe.kyokobot.libdave.callbacks;

/**
 * Receives log messages from the native DAVE library.
 * <p>
 * Messages may be delivered from any thread that is using the library, including native worker threads.
 */
@FunctionalInterface
public interface DaveLogSink {
    /**
     * Called for each log message emitted by the native library.
     *
     * @param severity The severity of the message: 0 (verbose), 1 (info), 2 (warning), 3 (error).
     * @param file     The native source file that emitted the message, or an empty string if unknown.
     * @param line     The line in {@code file} that emitted the message.
     * @param message  The log message.
     */
    void log(int severity, String file, int line, String message);
}
