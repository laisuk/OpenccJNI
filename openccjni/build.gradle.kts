plugins {
    id("java")
}

group = "io.github.laisuk"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// 👇 Add this to set the Automatic-Module-Name in MANIFEST.MF
tasks.jar {
    manifest {
        attributes(
            "Automatic-Module-Name" to "io.github.laisuk.openccjni",
            "Implementation-Title" to "OpenccJNI",
            "Implementation-Version" to project.version
        )
    }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}