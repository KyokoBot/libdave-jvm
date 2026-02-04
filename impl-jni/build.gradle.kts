dependencies {
    compileOnly("org.jetbrains:annotations:26.0.2")
    compileOnly("io.netty:netty-buffer:4.2.10.Final")
    implementation("dev.arbjerg:lava-common:1.5.4")
    api(project(":api"))

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(testFixtures(project(":api")))
    testImplementation("ch.qos.logback:logback-classic:1.3.16")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly(files("../natives/src/main/resources/")) // for the native libraries
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    pom {
        name = "jni"
    }
}