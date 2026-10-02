# libdave-jvm - native libraries

This directory contains the native libraries for the libdave-jvm project.

We vendor everything using [git-subrepo](https://github.com/ingydotnet/git-subrepo). No internet access or external build tools like `vcpkg` are required.

## Building

```bash
# macOS x86_64
cmake -S . -B build-darwin-x86_64 -G "Ninja Multi-Config" -DCMAKE_OSX_ARCHITECTURES=x86_64
cmake --build build-darwin-x86_64 --config Release --parallel

```

## Sanitizer (ASAN/UBSAN) builds

The whole tree (JNI glue, libdave, mlspp) can be built with AddressSanitizer and
UndefinedBehaviorSanitizer via `-DENABLE_SANITIZERS=ON`. IPO/LTO must be disabled
for sanitizer builds - ThinLTO bitcode breaks the sanitizer-instrumented link.

```bash
cmake -S . -B asan-build -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++ \
  -DENABLE_SANITIZERS=ON \
  -DCMAKE_INTERPROCEDURAL_OPTIMIZATION:BOOL=OFF \
  -DJAVA_HOME=/path/to/jdk
ninja -C asan-build libdave-jvm.so
```

To use it, repack the natives JAR (`./gradlew :natives:build -Ptarget=x86_64-linux-gnu`)
or drop the `.so` into `src/main/resources/natives/linux-x86-64/`, then run the JVM
with the ASAN runtime preloaded:

```bash
ASAN_OPTIONS=detect_leaks=0:handle_segv=0:allow_user_segv_handler=1:fast_unwind_on_fatal=1:log_path=/var/log/asan \
LD_PRELOAD=$(clang -print-file-name=libclang_rt.asan-x86_64.so) \
java -jar Lavalink.jar
```

ASAN option notes:

- `handle_segv=0` + `allow_user_segv_handler=1` - the JVM installs its own SIGSEGV
  handler (used for null-check elimination and stack probes); without these ASAN
  fights it and reports instant false positives.
- `detect_leaks=0` - JVM internals leak by design, leak reports are pure noise.
- `log_path` - ASAN only writes a report file (`<log_path>.<pid>`) when it actually
  detects something, so this survives even if stdout is buried in app logs.

Verified to work with the `:impl-jni:test` suite under OpenJDK/HotSpot. Expect
roughly 2-3x CPU on the MLS/crypto code paths.

## Patches

### Markers

We use the following markers to mark changes:

**In C/C++ files:**
```c
/// KOE PATCH BEGIN
/// KOE PATCH END
```

**In CMake files:**
```cmake
## KOE PATCH BEGIN
## KOE PATCH END
```

### Current patches

- [mlspp/CMakeLists.txt](mlspp/CMakeLists.txt): Fixed clang-cl build.
- [libdave/cpp/includes/dave/dave.h](libdave/cpp/includes/dave/dave.h): Disabled symbol exports, as we only want to export the JNI symbols.
- [libdave/cpp/CMakeLists.txt](libdave/cpp/CMakeLists.txt): Fixed clang-cl build; skip vendored Debug-only sanitizer setup when sanitizers are configured by the parent project.
- [CMakeLists.txt](CMakeLists.txt): Respect user-provided `CMAKE_INTERPROCEDURAL_OPTIMIZATION=OFF` (required for sanitizer builds); propagate `ENABLE_SANITIZERS` to the JNI glue and mlspp.
