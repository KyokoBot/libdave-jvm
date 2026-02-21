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
if (target != null && target.isNotEmpty()) {
    include("natives")
}
