package moe.kyokobot.libdave.ffm;

import com.sedmelluq.lava.common.natives.NativeLibraryProperties;
import com.sedmelluq.lava.common.natives.ResourceNativeLibraryBinaryProvider;
import com.sedmelluq.lava.common.natives.SystemNativeLibraryProperties;
import com.sedmelluq.lava.common.natives.architecture.SystemType;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class NativeLibrary {
    private static final String LIBRARY_NAME = "dave-jvm";

    private NativeLibrary() {
    }

    // lava-common's NativeLibraryLoader binds the library to its own class loader via System.load, which
    // SymbolLookup.loaderLookup() can't see from other class loaders. Load it by path instead, keeping the same
    // resource layout and properties so the natives artifacts are shared with impl-jni.
    static SymbolLookup load() {
        return SymbolLookup.libraryLookup(locate(), Arena.global());
    }

    private static Path locate() {
        NativeLibraryProperties properties = new SystemNativeLibraryProperties(LIBRARY_NAME, "lava.native.");
        String explicitPath = properties.getLibraryPath();
        if (explicitPath != null) {
            return Paths.get(explicitPath);
        }

        SystemType systemType = SystemType.detect(properties);
        ResourceNativeLibraryBinaryProvider provider =
                new ResourceNativeLibraryBinaryProvider(NativeLibrary.class, "/natives/");

        try (InputStream library = provider.getLibraryStream(systemType, LIBRARY_NAME)) {
            if (library == null) {
                throw new UnsatisfiedLinkError("Native library " + LIBRARY_NAME + " is not available for "
                        + systemType.formatSystemName());
            }

            Path directory = Files.createTempDirectory("libdave-jvm-");
            Path file = directory.resolve(systemType.formatLibraryName(LIBRARY_NAME));
            Files.copy(library, file);
            // Deleted in reverse order of registration: the file first, then its directory.
            directory.toFile().deleteOnExit();
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract native library " + LIBRARY_NAME, e);
        }
    }
}
