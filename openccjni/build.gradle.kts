import java.net.URL
import java.net.HttpURLConnection
import java.util.Base64

plugins {
    id("java")
    id("signing")
    `maven-publish`
}

group = "io.github.laisuk"
version = "1.0.0"

repositories {
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
    if (JavaVersion.current().isJava9Compatible) {
        options.release.set(8)
    } else {
        // Fallback for Gradle running on Java 8
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}

// Restrict the sources JAR to Java sources only
tasks.named<Jar>("sourcesJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // allow only actual Java sources & descriptors
    include("**/*.java", "**/module-info.java", "**/package-info.java")
    // hard-exclude everything JNI/native-ish living under src/main/java/openccjni
    exclude(
        "**/natives/**",
        "**/*.so", "**/*.dll", "**/*.dylib",
        "**/*.h", "**/*.hpp", "**/*.cpp", "**/*.c",
        "**/*.txt", "**/*.bin"
    )
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

// 👇 Automatic-Module-Name + reproducible jars
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

// NEW: Publish to mavenLocal()
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "openccjni"       // change if you prefer another artifactId
            version = project.version.toString()

            pom {
                name.set("OpenccJNI")
                description.set("Java JNI wrapper for Rust-based C API opencc-fmmseg")
                url.set("https://github.com/laisuk/OpenccJNI")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://raw.githubusercontent.com/laisuk/OpenccJNI/master/LICENSE")
                    }
                }
                developers {
                    developer {
                        id.set("laisuk")
                        name.set("Laisuk Lai")
                        url.set("https://github.com/laisuk")
                    }
                }
                scm {
                    url.set("https://github.com/laisuk/OpenccJNI")
                    connection.set("scm:git:https://github.com/laisuk/OpenccJNI.git")
                    developerConnection.set("scm:git:ssh://git@github.com/laisuk/OpenccJNI.git")
                }
            }
        }
    }
    repositories {
        maven {
            name = "ossrh-staging-api"
            // Staging for releases:
            url = uri(
                if (version.toString().endsWith("SNAPSHOT"))
                    "https://central.sonatype.com/repository/maven-snapshots/"
                else
                    "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
            )
            credentials {
                username = System.getenv("OSSRH_USERNAME") ?: findProperty("ossrhUsername") as String?
                password = System.getenv("OSSRH_PASSWORD") ?: findProperty("ossrhPassword") as String?
            }
        }
    }
}

signing {
    useGpgCmd()
    // Only sign if we’re not publishing locally
    val isLocal =
        gradle.startParameter.taskNames.any { it.contains("publishToMavenLocal") || it.contains("LocalOutput") }
    if (!isLocal) {
        sign(publishing.publications["mavenJava"])
    }
}

val portalUser = (System.getenv("OSSRH_USERNAME") ?: findProperty("ossrhUsername") as String?)
val portalPass = (System.getenv("OSSRH_PASSWORD") ?: findProperty("ossrhPassword") as String?)
val portalAuth: String = Base64.getEncoder().encodeToString("${portalUser}:${portalPass}".toByteArray())

// Use your root namespace (groupId root), e.g. "io.github.laisuk"
val portalNamespace = "io.github.laisuk"

// Triggers the Portal to ingest the staging upload so it shows up in central.sonatype.com
tasks.register("uploadToPortal") {
    group = "publishing"
    description = "Notify Central Portal to ingest the last staging upload"
    doLast {
        val user = portalUser ?: ""
        val pass = portalPass ?: ""
        require(user.isNotEmpty() && pass.isNotEmpty()) {
            "Missing Central Portal credentials (CENTRAL_PORTAL_TOKEN_USER/_PASS or gradle.properties)."
        }

        val auth = Base64.getEncoder().encodeToString("$user:$pass".toByteArray(Charsets.UTF_8))
        val urlStr = "https://ossrh-staging-api.central.sonatype.com/" +
                "manual/upload/defaultRepository/$portalNamespace?publishing_type=user_managed"

        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $auth")
            doOutput = true           // POST (nobody)
            useCaches = false
            connectTimeout = 30_000
            readTimeout = 60_000
        }

        // Nobody to send; just open/close the stream to issue the request
        conn.outputStream.use { /* empty POST body */ }

        val code = conn.responseCode
        val body = try {
            (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
        } catch (_: Exception) {
            ""
        }

        conn.disconnect()

        if (code !in 200..299) {
            throw GradleException("Portal upload failed: $code ${body.take(500)}")
        } else {
            println("Portal upload ok: $code")
        }
    }
}

// Only wire the ingestion step for non-SNAPSHOT publishes
if (!version.toString().endsWith("SNAPSHOT")) {
    tasks.named("publish") { finalizedBy("uploadToPortal") }
}