plugins {
    id("java")
    application
}

group = "io.github.laisuk"
version = "1.2.0"

repositories {
    mavenCentral()
}

dependencies {
    // core library
    implementation(project(":openccjni"))
    // CLI parser
    implementation("info.picocli:picocli:4.7.7")
    // Generate GraalVM reflection config for picocli automatically
//    annotationProcessor("info.picocli:picocli-codegen:4.7.7")
    //PDFBox
    implementation("org.apache.pdfbox:pdfbox:3.0.6")
}

// Application entrypoint (used by `run`, Jar manifest, etc.)
application {
    mainClass.set("openccjnicli.Main")
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-XX:+IgnoreUnrecognizedVMOptions",
        "--enable-native-access=ALL-UNNAMED"
    )
}

// Make sure the CLI JAR is runnable (even though we ship a native exe as well)
tasks.withType<Jar> {
    manifest {
        attributes(
            "Main-Class" to application.mainClass.get(),
            "Implementation-Title" to "OpenccJniCli",
            "Implementation-Version" to project.version
            // Note: we intentionally do NOT set Automatic-Module-Name for CLI (no need)
        )
    }
}

// --- Optional: a “fat JAR” for JVM-only users (kept, micro-tuned & documented) ---
val fatJar = tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Assembles a runnable fat JAR (CLI + deps)."

    manifest {
        attributes("Main-Class" to application.mainClass.get())
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })

    archiveClassifier.set("all")
}
