package moe.kyokobot.libdave;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks that the JVM exits cleanly while native objects, callbacks and a log sink are still alive.
 */
public abstract class ProcessExitTestBase {
    abstract Class<? extends DaveFactory> getFactoryClass();

    @Test
    public void systemExitWithLiveNativeState() throws Exception {
        assertCleanExit("exit");
    }

    @Test
    public void mainReturnWithLiveNativeState() throws Exception {
        assertCleanExit("return");
    }

    private void assertCleanExit(String mode) throws Exception {
        File output = File.createTempFile("libdave-exit-", ".log");
        File crashDir = Files.createTempDirectory("libdave-exit-").toFile();
        try {
            List<String> command = new ArrayList<>();
            command.add(new File(System.getProperty("java.home"), "bin/java").getPath());
            for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (arg.startsWith("--enable-native-access") || arg.startsWith("-Dlava.")
                        || arg.startsWith("-Dlibdave.") || arg.startsWith("-Dio.netty.")) {
                    command.add(arg);
                }
            }
            command.add("-XX:-CreateCoredumpOnCrash");
            command.add("-XX:ErrorFile=" + new File(crashDir, "hs_err_%p.log").getPath());
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(ExitWithLiveNativeState.class.getName());
            command.add(getFactoryClass().getName());
            command.add(mode);

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(output)
                    .start();
            boolean exited = process.waitFor(60, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
            }
            String log = new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8);

            assertTrue(exited, "JVM did not exit:\n" + log);
            assertTrue(log.contains(ExitWithLiveNativeState.READY), "setup did not complete:\n" + log);
            assertEquals(0, process.exitValue(), "JVM did not exit cleanly:\n" + log);
        } finally {
            output.delete();
            File[] crashReports = crashDir.listFiles();
            if (crashReports != null) {
                for (File report : crashReports) {
                    report.delete();
                }
            }
            crashDir.delete();
        }
    }
}
