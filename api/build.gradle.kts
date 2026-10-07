plugins {
    id("java-test-fixtures")
}

dependencies {
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.netty.buffer)

    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesApi(libs.netty.buffer)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform)
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    pom {
        name = "api"
    }
}
