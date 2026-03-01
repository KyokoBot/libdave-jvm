cmake_minimum_required(VERSION 3.26)
project(libdave-jvm LANGUAGES CXX C)

set(CMAKE_POSITION_INDEPENDENT_CODE TRUE)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

include(CheckIPOSupported)
check_ipo_supported(RESULT ipo_supported OUTPUT error)
if(ipo_supported)
  set(CMAKE_INTERPROCEDURAL_OPTIMIZATION TRUE)
  message(STATUS "LTO/IPO enabled")
else()
  message(WARNING "IPO is not supported: ${error}")
endif()

if(MINGW)
  message(FATAL_ERROR "MinGW is not supported, use clang-cl or MSVC instead")
endif()

# if we're on clang or gcc, enable prefix mapping
if(CMAKE_CXX_COMPILER_ID MATCHES "Clang" OR CMAKE_CXX_COMPILER_ID MATCHES "GNU")
  message(STATUS "Building with Clang or GCC")

  set(_flags_list
    "-frandom-seed=0"
    "-ffile-prefix-map=\"${CMAKE_SOURCE_DIR}\"=."
    "-fmacro-prefix-map=\"${CMAKE_SOURCE_DIR}\"=."
    "-fvisibility=hidden"
    "-fvisibility-inlines-hidden"
    "-ffunction-sections"
    "-fdata-sections"
  )

  # Check for clang-cl which requires -Xclang before these flags
  set(_is_clang_cl OFF)
  if (CMAKE_CXX_COMPILER_ID STREQUAL "Clang" AND CMAKE_CXX_SIMULATE_ID STREQUAL "MSVC")
    set(_is_clang_cl ON)
  endif()

  foreach(_flag ${_flags_list})
    if(_is_clang_cl)
      set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /clang:${_flag}")
      set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} /clang:${_flag}")
    else()
      set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${_flag}")
      set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${_flag}")
    endif()
  endforeach()
elseif(MSVC)
  message(STATUS "Building with MSVC")
else()
  message(WARNING "Building with unknown compiler: ${CMAKE_CXX_COMPILER_ID}")
endif()

if(MSVC)
  # Static CRT (/MT) to avoid msvcp140.dll version mismatch with JDK (constexpr mutex trap)
  set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()

# split debug symbols into a separate file into a separate file
if(APPLE)
  find_program(DSYMUTIL_PROGRAM dsymutil)
  if (DSYMUTIL_PROGRAM)
    foreach(lang C CXX)
      foreach(var LINK_EXECUTABLE CREATE_SHARED_LIBRARY)
        set(CMAKE_${lang}_${var} "${CMAKE_${lang}_${var}}" "${DSYMUTIL_PROGRAM} <TARGET>")
      endforeach()
    endforeach()
  endif()
endif()

set(MLS_CXX_NAMESPACE "mlspp" CACHE STRING "Top-level Namespace for CXX")
set(MLS_EXPORT_NAMESPACE "MLSPP" CACHE STRING "Namespace for CMake Export")
option(OPENSSL_SMALL "Use small OpenSSL library" ON)
option(DISABLE_GREASE "Disables the inclusion of MLS protocol recommended GREASE values" ON)
option(DISABLE_PQ "Disables support for PQ algorithms even when they would otherwise be enabled" ON)
option(REQUIRE_BORINGSSL "Require BoringSSL instead of OpenSSL" ON)
option(TESTING "Build tests" OFF)
option(BUILD_TESTING "Build tests" OFF)
option(PERSISTENT_KEYS "Enable storage of persistent signature keys" OFF)
option(BUILD_SHARED_LIBS "Build shared libraries" OFF)
option(ENABLE_SANITIZERS "Enable address and undefined behavior sanitizers" OFF)

set(OpenSSL_DIR "${CMAKE_CURRENT_SOURCE_DIR}/cmake" CACHE PATH "vendored openssl")
set(MLSPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/cmake" CACHE PATH "vendored mlspp")

set(CMAKE_FIND_PACKAGE_PREFER_CONFIG TRUE)
add_subdirectory(boringssl)

# don't waste time building certain targets that we don't need
set_target_properties(
  bssl # boringssl executable
  PROPERTIES
  EXCLUDE_FROM_ALL TRUE
)
set_target_properties(
  ssl # boringssl
  PROPERTIES
  EXCLUDE_FROM_ALL TRUE
)
set_target_properties(
  decrepit # boringssl
  PROPERTIES
  EXCLUDE_FROM_ALL TRUE
)
set_target_properties(
  pki # boringssl
  PROPERTIES
  EXCLUDE_FROM_ALL TRUE
)
# find_package(OpenSSL REQUIRED)

set(nlohmann_json_DIR "${CMAKE_CURRENT_SOURCE_DIR}/json-cpp")
add_subdirectory(mlspp)
add_subdirectory(libdave/cpp)

find_package(JNI)
if(NOT JNI_FOUND)
  message(FATAL_ERROR "JNI not found - did you forget to set JAVA_HOME?")
endif()

set(DJ_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp)
add_library(dave-jvm SHARED
  ${DJ_ROOT}/bind_decryptor.cpp
  ${DJ_ROOT}/bind_encryptor.cpp
  ${DJ_ROOT}/bind_key_ratchet.cpp
  ${DJ_ROOT}/bind_logging.cpp
  ${DJ_ROOT}/bind_session.cpp
)

target_link_libraries(dave-jvm PRIVATE libdave JNI::JNI MLSPP::mlspp OpenSSL::Crypto)
target_compile_features(dave-jvm PRIVATE cxx_std_17)
target_include_directories(dave-jvm PRIVATE
  ${PROJECT_SOURCE_DIR}/libdave/cpp/includes
)
# Output shared lib and PDB at build root so Gradle moveResources and CI artifact collection find them
foreach(_config Release Debug RelWithDebInfo MinSizeRel)
  set_target_properties(dave-jvm PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY_${_config} "${CMAKE_BINARY_DIR}"
    LIBRARY_OUTPUT_DIRECTORY_${_config} "${CMAKE_BINARY_DIR}"
    ARCHIVE_OUTPUT_DIRECTORY_${_config} "${CMAKE_BINARY_DIR}"
    PDB_OUTPUT_DIRECTORY_${_config} "${CMAKE_BINARY_DIR}"
    COMPILE_PDB_OUTPUT_DIRECTORY_${_config} "${CMAKE_BINARY_DIR}")
endforeach()

if(LINUX)
  target_link_options(dave-jvm PRIVATE "-Wl,--version-script,${DJ_ROOT}/libdave.exports" "-Wl,--build-id" "-static-libstdc++" "-static-libgcc")
  add_custom_command(TARGET dave-jvm POST_BUILD
    COMMAND ${CMAKE_STRIP} --only-keep-debug -o $<TARGET_FILE:dave-jvm>.debug $<TARGET_FILE:dave-jvm>
    COMMAND echo "Debug symbols saved to $<TARGET_FILE:dave-jvm>.debug"
    COMMAND ${CMAKE_STRIP} --strip-debug $<TARGET_FILE:dave-jvm>
    COMMAND echo "Debug symbols stripped from $<TARGET_FILE:dave-jvm>")
endif()
