plugins {
    application
}

// The ONLY package source is the local file repo staged by the publish workflow
// (download artifact "maven-repo" → ../localrepo). mavenCentral is a fallback for any
// transitive deps; the base jar is self-contained so in practice nothing else is pulled.
repositories {
    val localRepo = (findProperty("localRepo") as String?) ?: "../localrepo"
    maven { url = uri(file(localRepo)) }
    mavenCentral()
}

// CI passes -PpagmoVersion=<resolved version>; fail loudly if it is missing so a
// clean-room run can never silently resolve the wrong (or no) artifact.
val pagmoVersion: String =
    (findProperty("pagmoVersion") as String?)
        ?: error("Set -PpagmoVersion=<version> (the published pagmonet4j version to test).")

dependencies {
    implementation("io.github.pagmonet:pagmonet4j:$pagmoVersion")
}

application {
    mainClass.set("cleanroom.CleanRoomMain")
}

// The app signals success/failure via System.exit; JavaExec propagates a non-zero exit
// as a build failure, which fails the workflow step — that is the gate.
tasks.named<JavaExec>("run") {
    // No PAGMO4J_NATIVE_DIR, no java.library.path, no DYLD/PATH hints: the artifact
    // must be entirely self-contained.
}
