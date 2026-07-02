plugins {
    java
    kotlin("jvm") version "2.1.20" apply false
}

subprojects {
    // Single-sourced from gradle.properties (group/version) so a release is one edit.
    // CI can override at invocation with -Pversion=<tag>.
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }
}
