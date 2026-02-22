plugins {
    id("java-test-fixtures")
}

dependencies {
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.netty.buffer)

    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesApi(libs.netty.buffer)
}

mavenPublishing {
    pom {
        name = "api"
    }
}
