plugins {
    id("java")
    application
}

group = "io.github.laisuk"
version = "1.2.2"

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
    implementation("org.apache.pdfbox:pdfbox:3.0.7")
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

// Helper: turn "1.8"/"8"/"17" into major bytecode (52/61/etc.)
fun majorFromJavaVersion(vRaw: String): Int {
    val n = if (vRaw.startsWith("1.")) vRaw.substring(2) else vRaw
    return n.toInt() + 44
}

// Make sure the CLI JAR is runnable (even though we ship a native exe as well)
tasks.withType<Jar>().configureEach {
    doFirst {
        val cj = tasks.withType<JavaCompile>().findByName("compileJava")
        val rawVer = when {
            cj?.options?.release?.isPresent == true -> cj.options.release.get().toString()
            cj != null -> cj.targetCompatibility
            else -> JavaVersion.current().toString()
        }
        val bytecodeJava = if (rawVer == "8") "1.8" else rawVer
        val major = majorFromJavaVersion(bytecodeJava)
        manifest {
            attributes(
                "Main-Class" to application.mainClass.get(),
                "Implementation-Title" to "OpenccJniCli",
                "Implementation-Version" to project.version,
                "Major-Bytecode-Number" to major.toString(),
                "Bytecode-Java-Version" to bytecodeJava
                // Note: we intentionally do NOT set Automatic-Module-Name for CLI (no need)
            )
        }
    }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
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
