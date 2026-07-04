// PagmoNet4j.ipopt is a PURE, EPL-2.0 NATIVE PAYLOAD (the Java twin of Pagmo.NET.Ipopt).
// It contains no Java or pagmo code of its own.
//
// It bundles the IPOPT runtime library (libipopt) and its dependency closure (MUMPS,
// OpenBLAS, the GCC runtime, ...) under natives/<rid>/. The base pagmonet4j artifact already
// contains the `ipopt` algorithm, which loads libipopt at runtime via dlopen; its NativeLoader
// extracts this payload's natives/<rid>/ closure from the classpath alongside the wrapper.
//
// Consumers depend on BOTH pagmonet4j and pagmonet4j-ipopt (this artifact depends on the
// base). Anyone who prefers to bring their own libipopt (a system install, or the
// PAGMONET_IPOPT_LIBRARY override) can depend on the base pagmonet4j artifact alone.

plugins {
    `java-library`
    `maven-publish`
}

// group + version are single-sourced from gradle.properties (CI overrides version with
// -Pversion=<tag>); do not hardcode them here.

java {
    // No source of our own, but produce the (empty) sources/javadoc jars Maven Central expects.
    withSourcesJar()
    withJavadocJar()
}

// Name the jar after the published artifactId so build/libs/*.jar matches the Maven coordinate.
base {
    archivesName.set("pagmonet4j-ipopt")
}

repositories {
    mavenCentral()
    mavenLocal() // the base pagmonet4j is resolved from here in local/CI builds
}

dependencies {
    // The base carries the ipopt algorithm + bindings; this payload supplies the native IPOPT
    // it dlopen's. Kept in lockstep by the shared version.
    api("${project.group}:pagmonet4j:${project.version}")
}

// ── Native payload ──────────────────────────────────────────────────────────────────
// CI stages each platform's IPOPT closure (libipopt + its dependency dylibs/DLLs, produced by
// scripts/bundle-native-deps.ps1) under staged-natives/<rid>/. Bundle each into the jar at
// natives/<rid>/ so the base NativeLoader can extract the whole closure at runtime. Builds
// without staged natives still produce a (payload-less) jar so local dev/test resolves.
tasks.processResources {
    val stagedNatives = rootProject.projectDir.resolve("staged-natives")
    if (stagedNatives.isDirectory) {
        stagedNatives.listFiles()?.filter { it.isDirectory }?.forEach { ridDir ->
            from(ridDir) { into("natives/${ridDir.name}") }
        }
    }
}

// SWIG-generated code (in the base) has missing tags; harmless here but keep doclint quiet.
tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

publishing {
    repositories {
        mavenLocal()
        // File-based repo the CI clean-room job consumes to prove the published-shaped
        // artifact works on a machine with no dev tools, no conda, no native env hints.
        maven {
            name = "BuildLocal"
            url = layout.buildDirectory.dir("localrepo").get().asFile.toURI()
        }
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/samthegliderpilot/PagmoNet4j.ipopt")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            artifactId = "pagmonet4j-ipopt"
            from(components["java"])
            pom {
                name.set("pagmonet4j-ipopt")
                description.set("IPOPT native runtime for PagmoNet4j: bundles libipopt and its dependency closure so pagmonet4j's built-in ipopt algorithm works out of the box. Add this alongside pagmonet4j.")
                url.set("https://github.com/samthegliderpilot/PagmoNet4j.ipopt")
                licenses {
                    license {
                        name.set("EPL-2.0")
                        url.set("https://www.eclipse.org/legal/epl-2.0/")
                    }
                }
                developers {
                    developer {
                        id.set("samthegliderpilot")
                        name.set("samthegliderpilot")
                        email.set("samthegliderpilot@gmail.com")
                    }
                }
                scm {
                    url.set("https://github.com/samthegliderpilot/PagmoNet4j.ipopt")
                    connection.set("scm:git:git://github.com/samthegliderpilot/PagmoNet4j.ipopt.git")
                    developerConnection.set("scm:git:ssh://github.com/samthegliderpilot/PagmoNet4j.ipopt.git")
                }
                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/samthegliderpilot/PagmoNet4j.ipopt/issues")
                }
            }
        }
    }
}
