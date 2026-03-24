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
    if (findProperty("target") != null) {
        testRuntimeOnly(project(":natives"))
    } // for the native libraries
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    pom {
        name = "jni"
    }
}