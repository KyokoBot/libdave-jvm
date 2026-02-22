dependencies {
    api(projects.api)
    api(projects.implJni)
}

val target = ext["target"]?.toString()
    ?: throw GradleException("natives project requires property 'target' (set -Ptarget= or target in gradle.properties)")
val platform = ext["platform"]?.toString()
    ?: throw GradleException("natives project requires 'platform' derived from target")
val artifactName = "natives-$platform"

val nativeLibNames = setOf("libdave-jvm.so", "libdave-jvm.dylib", "dave-jvm.dll")
val buildDir = layout.projectDirectory.dir("cmake-build-$target")
fun requireNativeLibBuilt() {
    if (!buildDir.asFile.exists()) {
        throw GradleException("Native library not built. Build with cmake in natives/cmake-build-$target/ first.")
    }
    val hasLib = buildDir.asFile.listFiles()?.any { it.name in nativeLibNames } == true
    if (!hasLib) {
        throw GradleException("Native library (libdave-jvm.so, libdave-jvm.dylib, or dave-jvm.dll) not found in natives/cmake-build-$target/")
    }
}
logger.lifecycle("Target: $target, Platform: $platform, Artifact Name: $artifactName")

base {
    archivesName = artifactName
}

tasks.named<Copy>("processResources") {
    doFirst { requireNativeLibBuilt() }
    from("cmake-build-$target/") {
        include("libdave-jvm.so", "libdave-jvm.dylib", "dave-jvm.dll")
        into("natives/$platform")
    }
}

tasks.register<Delete>("cleanNatives") {
    group = "build"
    delete(fileTree("src/main/resources/natives"))
    tasks["clean"].dependsOn(this)
}

mavenPublishing {
    pom {
        name = artifactName
    }
}
