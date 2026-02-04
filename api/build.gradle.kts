plugins {
    id("java-test-fixtures")
}

dependencies {
    compileOnly("org.jetbrains:annotations:26.0.2")
    compileOnly("io.netty:netty-buffer:4.2.10.Final")

    testFixturesImplementation(platform("org.junit:junit-bom:5.11.4"))
    testFixturesImplementation("org.junit.jupiter:junit-jupiter")
    testFixturesApi("io.netty:netty-buffer:4.2.10.Final")
}

mavenPublishing {
    pom {
        name = "api"
    }
}
