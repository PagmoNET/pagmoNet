import java.time.Duration

plugins {
    java
    `maven-publish`
}

// Single source of truth for the Java language level: gradle.properties `javaVersion`.
val javaLangVersion = JavaVersion.toVersion(providers.gradleProperty("javaVersion").getOrElse("17"))

java {
    sourceCompatibility = javaLangVersion
    targetCompatibility = javaLangVersion
    withSourcesJar()
    withJavadocJar()
}

// SWIG-generated sources live in src/generated/java — committed alongside hand-written sources.
sourceSets {
    main {
        java {
            srcDirs("src/main/java", "src/generated/java")
        }
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// SWIG-generated code has missing @param/@return tags — disable doclint so javadoc doesn't fail.
tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

// src/main/java and src/generated/java may both contribute the same package paths.
tasks.named<Jar>("sourcesJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Bundle the native library into the JAR under /natives/<rid>/ so NativeLoader
// can extract it at runtime without requiring PAGMO4J_NATIVE_DIR or java.library.path.
// Only included when the native has been built locally — the build still works without it.
tasks.processResources {
    val winDll = rootProject.projectDir.resolve("pagmoWrapper/win-build/pagmonet4j.dll")
    if (winDll.exists()) {
        from(winDll.parentFile) {
            include("pagmonet4j.dll")
            into("natives/win-x64")
        }
    }
    val linuxSo = rootProject.projectDir.resolve("pagmoWrapper/build/libpagmonet4j.so")
    if (linuxSo.exists()) {
        from(linuxSo.parentFile) {
            include("libpagmonet4j.so")
            into("natives/linux-x64")
        }
    }
    // The base build produces a single-arch dylib for the host. A copy spec only honours
    // its LAST into(), so the two RIDs must be separate from{} blocks or the dylib lands
    // in only one of them (and the other arch's lookup misses at runtime).
    val macDylib = rootProject.projectDir.resolve("pagmoWrapper/build/libpagmonet4j.dylib")
    if (macDylib.exists()) {
        from(macDylib.parentFile) {
            include("libpagmonet4j.dylib")
            into("natives/osx-arm64")
        }
        from(macDylib.parentFile) {
            include("libpagmonet4j.dylib")
            into("natives/osx-x64")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    // Resolve PAGMO4J_NATIVE_DIR against the root project dir so relative paths like
    // "pagmoWrapper/win-build" work correctly even though test JVMs run with the
    // subproject (core/) as their working directory.
    val nativeDir = System.getenv("PAGMO4J_NATIVE_DIR")
        ?.let { rootProject.projectDir.resolve(it).absolutePath }
        ?: "."
    systemProperty("java.library.path", nativeDir)
    // JNI-heavy tests are more stable when each test class gets a fresh worker process.
    forkEvery = 1
    maxParallelForks = 1
    // Hard per-test timeout so a hung test fails in 15s instead of blocking the suite.
    systemProperty("junit.jupiter.execution.timeout.default", "15s")
    // Process-level timeout kills the JVM if native code hangs (JUnit timeout can't interrupt native threads).
    timeout.set(Duration.ofMinutes(5))
    testLogging {
        // Show the full exception chain — includes UnsatisfiedLinkError message text
        // and all Caused-by entries so native-load failures are visible in CI logs.
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
    }
}

publishing {
    repositories {
        mavenLocal()
        // File-based repo the CI clean-room job consumes to prove the published-shaped
        // base artifact works on a machine with no dev tools and no native env hints.
        maven {
            name = "BuildLocal"
            url = layout.buildDirectory.dir("localrepo").get().asFile.toURI()
        }
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/PagmoNET/pagmoNet")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            artifactId = "pagmonet4j"
            from(components["java"])
            pom {
                name.set("pagmonet4j")
                description.set("Java/Kotlin bindings for pagmo2 — multi-island metaheuristic optimization")
                url.set("https://github.com/PagmoNET/pagmoNet")
                licenses {
                    license {
                        name.set("MPL-2.0")
                        url.set("https://www.mozilla.org/en-US/MPL/2.0/")
                    }
                }
            }
        }
    }
}
