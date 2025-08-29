import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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
    val isLocal = gradle.startParameter.taskNames.any { it.contains("publishToMavenLocal") || it.contains("LocalOutput") }
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
        require(!portalUser.isNullOrBlank() && !portalPass.isNullOrBlank()) {
            "Missing OSSRH portal credentials (OSSRH_USERNAME/OSSRH_PASSWORD or ossrhUsername/ossrhPassword)."
        }
        val url = "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/$portalNamespace"
        val client = HttpClient.newHttpClient()
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $portalAuth")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            throw RuntimeException("Portal upload failed: ${resp.statusCode()} ${resp.body()}")
        } else {
            println("Portal upload ok: ${resp.statusCode()}")
        }
    }
}

// Typical CI sequence: publish → uploadToPortal
tasks.named("publish") { finalizedBy("uploadToPortal") }