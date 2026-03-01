# libdave-jvm

**Discord Audio & Video End-to-End Encryption (DAVE) for Java.**

This repository contains a Java implementation of [Discord's E2EE DAVE protocol](https://daveprotocol.com/) supporting libraries.

This project only provides the cryptographic support and the complex parts of the MLS protocol - it does not communicate with any servers directly. 
It is intended to be consumed by other libraries that interact with Discord's voice servers or API, such as [Koe](https://github.com/KyokoBot/koe) or [JDA](https://github.com/discord-jda/JDA).

Minimum Java version is 8. See below for native dependency compatibility.

## Modules

- **`api`**: Defines the common Java interfaces for the DAVE protocol (Session, Encryptor, Decryptor, etc.).
- **`impl-jni`**: An implementation of the API that binds to the official C++ `libdave` using JNI. Strongly recommended for production use.
- **`natives`**: Contains the CMake project for `libdave` JNI bindings and supporting Gradle project that handles publishing the natives to a Maven repository.

## Usage

### Usage with Netty

To use the libdave-jvm with Netty's `ByteBuf`, you can add an optional dependency on Netty Buffer and use the utility classes provided in `moe.kyokobot.libdave.netty`.

**Note:** The Netty integration is *optional*. The `moe.kyokobot.libdave.netty` package is not usable unless you manually 
add the Netty dependency, because `netty-buffer` is not declared as a transitive dependency. Attempting to use these classes 
without Netty in your classpath will result in a `NoClassDefFoundError`.

**Add to your dependencies:**
```kotlin
implementation("io.netty:netty-buffer:4.2.10.Final")
```

See the `moe.kyokobot.libdave.netty` package documentation for further details.

### Dependencies

**Gradle (Kotlin DSL):**

```kotlin
repositories {
    // mavenCentral() // not published yet
    maven {
        url = uri("https://maven.lavalink.dev/snapshots")
    }
}

// Use first 9 charactesr of the commit hash for git versions.

dependencies {
    // This will transitively include the `api` module.
    implementation("moe.kyokobot.libdave:impl-jni:VERSION")

    // Linux (glibc 2.28 / EL8)
    implementation("moe.kyokobot.libdave:natives-linux-x86-64:VERSION")
    // Linux (glibc 2.35)
    implementation("moe.kyokobot.libdave:natives-linux-x86:VERSION")
    implementation("moe.kyokobot.libdave:natives-linux-aarch64:VERSION")
    implementation("moe.kyokobot.libdave:natives-linux-arm:VERSION")

    // Linux (musl)
    implementation("moe.kyokobot.libdave:natives-linux-musl-x86-64:VERSION")
    implementation("moe.kyokobot.libdave:natives-linux-musl-x86:VERSION")
    implementation("moe.kyokobot.libdave:natives-linux-musl-aarch64:VERSION")
    implementation("moe.kyokobot.libdave:natives-linux-musl-arm:VERSION")

    // Windows
    implementation("moe.kyokobot.libdave:natives-win-x86-64:VERSION")
    implementation("moe.kyokobot.libdave:natives-win-x86:VERSION")
    implementation("moe.kyokobot.libdave:natives-win-aarch64:VERSION")

    // macOS
    implementation("moe.kyokobot.libdave:natives-darwin:VERSION") // Universal Intel + Apple Silicon
}
```

### Usage with JDA

> [!IMPORTANT]
> We recommend using [JDAVE](https://github.com/MinnDevelopment/jdave) with JDA, unless you have major blockers preventing you from using Java 25+ or you use an unsupported architecture/platform configuration.

**Gradle (Kotlin DSL):**

```kotlin
repositories {
    // mavenCentral() // not published yet
    maven {
        url = uri("https://maven.lavalink.dev/snapshots")
    }
}

// Use first 9 charactesr of the commit hash for git versions.

dependencies {
    // This will transitively include the `api` module.
    implementation("moe.kyokobot.libdave:adapter-jda:VERSION")
    implementation("moe.kyokobot.libdave:impl-jni:VERSION")

    // See above for natives
    // implementation("moe.kyokobot.libdave:natives-PLATFORM-ARCH:VERSION")
}
```

**Implementation**

```java
DaveFactory daveFactory = new NativeDaveFactory(); // Using native libdave via jni-impl

DaveSessionFactory daveSessionFactory = new LDJDADaveSessionFactory(daveFactory);

jdaBuilder
    .setAudioModuleConfig(new AudioModuleConfig()
        .withDaveSessionFactory(daveSessionFactory))
    .build()
```

## License

This project is licensed under the Apache 2.0 License. See the [LICENSE](LICENSE) file for details.
