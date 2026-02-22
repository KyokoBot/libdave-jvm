rootProject.name = "libdave-jvm"

include("api")
include("impl-jni")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

val targetFromCmd = gradle.startParameter.projectProperties["target"]
val targetFromEnv = System.getenv("TARGET")
val targetFromProps = run {
    val f = File(rootDir, "gradle.properties")
    if (f.exists()) {
        java.util.Properties().apply { f.inputStream().use { load(it) } }.getProperty("target")
            ?.takeIf { it.isNotBlank() }
    } else null
}
val target = targetFromCmd ?: targetFromEnv ?: targetFromProps
if (!target.isNullOrEmpty()) {
    include("natives")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            library("jetbrains-annotations", "org.jetbrains", "annotations").version("26.0.2")
            library("netty-buffer", "io.netty", "netty-buffer").version("4.2.10.Final")
            library("lava-common", "dev.arbjerg", "lava-common").version("1.5.4")

            library("junit-bom", "org.junit", "junit-bom").version("5.11.4")
            library("junit-jupiter", "org.junit.jupiter", "junit-jupiter").version("")
            library("junit-platform", "org.junit.platform", "junit-platform-launcher").version("")


            library("logback", "ch.qos.logback", "logback-classic").version("1.3.16")
        }
    }
}