// mostly yanked from https://github.com/MinnDevelopment/udpqueue.rs/blob/master/build.gradle.kts
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import java.io.ByteArrayOutputStream

plugins {
    id("com.vanniktech.maven.publish") version "0.32.0" apply false
}

val gitVersionInfo = getGitVersion()
logger.lifecycle("Version: ${gitVersionInfo.version} (isCommitHash: ${gitVersionInfo.isCommitHash})")

subprojects {
    apply(plugin = "com.vanniktech.maven.publish")
    apply(plugin = "java-library")

    group = "moe.kyokobot.libdave"

    version = gitVersionInfo.version

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(8)
    }

    repositories {
        mavenLocal()
        mavenCentral()
    }

    // See https://github.com/sedmelluq/lavaplayer/blob/master/common/src/main/java/com/sedmelluq/lava/common/natives/architecture/DefaultArchitectureTypes.java
    // identifier is the suffix used after the system name
    fun getPlatform(triplet: String) = when {
        triplet.startsWith("x86_64") && "linux" in triplet && "musl" in triplet -> "linux-musl-x86-64"
        triplet.startsWith("i686") && "linux" in triplet && "musl" in triplet -> "linux-musl-x86"
        triplet.startsWith("aarch64") && "linux" in triplet && "musl" in triplet -> "linux-musl-aarch64"
        triplet.startsWith("arm") && "linux" in triplet && "musl" in triplet -> "linux-musl-arm"

        triplet.startsWith("x86_64") && "linux" in triplet -> "linux-x86-64"
        triplet.startsWith("i686") && "linux" in triplet -> "linux-x86"
        triplet.startsWith("aarch64") && "linux" in triplet -> "linux-aarch64"
        triplet.startsWith("arm") && "linux" in triplet -> "linux-arm"

        triplet.startsWith("x86_64") && "windows" in triplet -> "win-x86-64"
        triplet.startsWith("i686") && "windows" in triplet -> "win-x86"
        triplet.startsWith("aarch64") && "windows" in triplet -> "win-aarch64"
        triplet.startsWith("arm") && "windows" in triplet -> "win-arm"

        triplet.startsWith("x86_64") && "darwin" in triplet -> "darwin-x86-64"
        (triplet.startsWith("aarch64e") || "arm64e" in triplet) && "darwin" in triplet -> "darwin-arm64e"
        triplet.startsWith("aarch64") && "darwin" in triplet -> "darwin-arm64"
        "darwin" in triplet -> "darwin"

        else -> throw IllegalArgumentException("Unknown platform: $triplet")
    }

    // Resolve target the same way as settings.gradle.kts (property, then TARGET env) so natives gets ext["target"] when only TARGET is set
    val targetProp = (findProperty("target") as? String)?.takeIf { it.isNotBlank() }
        ?: System.getenv("TARGET")?.takeIf { it.isNotBlank() }
    if (targetProp != null) {
        ext["target"] = targetProp
        ext["platform"] = getPlatform(targetProp)
    }

    afterEvaluate {
        plugins.withId("com.vanniktech.maven.publish.base") {
            configure<PublishingExtension> {
                val mavenUsername = findProperty("MAVEN_USERNAME") as String?
                val mavenPassword = findProperty("MAVEN_PASSWORD") as String?
                if (!mavenUsername.isNullOrEmpty() && !mavenPassword.isNullOrEmpty()) {
                    repositories {
                        val snapshots = "https://maven.lavalink.dev/snapshots"
                        val releases = "https://maven.lavalink.dev/releases"

                        maven(if (gitVersionInfo.isCommitHash) snapshots else releases) {
                            credentials {
                                username = mavenUsername
                                password = mavenPassword
                            }
                        }
                    }
                } else {
                    logger.lifecycle("Not publishing to maven.lavalink.dev because credentials are not set")
                }
            }

            configure<MavenPublishBaseExtension> {
                coordinates(group.toString(), project.the<BasePluginExtension>().archivesName.get(), version.toString())
                val mavenCentralUsername = findProperty("MAVEN_CENTRAL_USERNAME") as String?
                val mavenCentralPassword = findProperty("MAVEN_CENTRAL_PASSWORD") as String?
                if (!mavenCentralUsername.isNullOrEmpty() && !mavenCentralPassword.isNullOrEmpty()) {
                    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, false)
                    if (!gitVersionInfo.isCommitHash) {
                        signAllPublications()
                    }
                } else {
                    logger.lifecycle("Not publishing to OSSRH due to missing credentials")
                }

                pom {
                    description.set("Discord Audio & Video End-to-End Encryption (DAVE) for Java.")
                    url.set("https://github.com/KyokoBot/libdave-jvm")
                    scm {
                        url.set("https://github.com/KyokoBot/libdave-jvm")
                        connection.set("scm:git:git://github.com/KyokoBot/libdave-jvm")
                        developerConnection.set("scm:git:ssh:git@github.com:KyokoBot/libdave-jvm")
                    }
                    licenses {
                        license {
                            name.set("The Apache Software License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("alula")
                            name.set("Alula")
                            email.set("git@alula.me")
                        }
                    }
                }
            }
        }
    }
}

data class VersionInfo(val version: String, val isCommitHash: Boolean)

fun getGitVersion(): VersionInfo {
    var versionStr = ByteArrayOutputStream()
    val result = exec {
        standardOutput = versionStr
        errorOutput = versionStr
        isIgnoreExitValue = true
        commandLine("git", "describe", "--exact-match", "--tags")
    }
    if (result.exitValue == 0) {
        return VersionInfo(versionStr.toString().trim(), false)
    }


    versionStr = ByteArrayOutputStream()
    exec {
        standardOutput = versionStr
        errorOutput = versionStr
        commandLine("git", "rev-parse", "--short", "HEAD")
    }

    return VersionInfo(versionStr.toString().trim(), true)
}
