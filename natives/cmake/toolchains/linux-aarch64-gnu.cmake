# Native aarch64 Linux (glibc).
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)
set(CMAKE_C_COMPILER clang)
set(CMAKE_CXX_COMPILER clang++)
set(CMAKE_STRIP llvm-strip)
set(CMAKE_AR llvm-ar)
set(CMAKE_NM llvm-nm)
set(CMAKE_RANLIB llvm-ranlib)

# Force 64K page alignment for compatibility with 16K/64K page-size kernels (e.g. Android, RHEL on ARM).
set(CMAKE_EXE_LINKER_FLAGS_INIT "-Wl,-z,max-page-size=65536")
set(CMAKE_SHARED_LINKER_FLAGS_INIT "-Wl,-z,max-page-size=65536")
