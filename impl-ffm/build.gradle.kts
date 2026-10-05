dependencies {
    api(projects.api)

    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.netty.buffer)
    implementation(libs.lava.common)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(testFixtures(projects.api))
    testImplementation(libs.logback)
    testRuntimeOnly(libs.junit.platform)
    testRuntimeOnly(files("../natives/src/main/resources/")) // for the native libraries
}

// The FFM API is final since Java 22.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(22)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // Netty disables Unsafe by default on Java 25+, which hides the ByteBuf memory address fast path.
    systemProperty("io.netty.noUnsafe", "false")
}

mavenPublishing {
    pom {
        name = "ffm"
    }
}
