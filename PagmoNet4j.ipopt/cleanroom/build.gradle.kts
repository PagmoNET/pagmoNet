plugins {
    application
}

// The ONLY package source is the local file repo staged by the publish workflow: it holds BOTH
// the pagmonet4j-ipopt companion payload AND the same-version base pagmonet4j (the JNI wrapper +
// the ipopt algorithm), both built in the same run. Resolution is fully offline, so the gate
// proves base + companion together without anything being published. mavenCentral is a fallback
// for any incidental transitive deps.
repositories {
    val localRepo = (findProperty("localRepo") as String?) ?: "../localrepo"
    maven { url = uri(file(localRepo)) }
    mavenCentral()
}

// CI passes -PpagmoIpoptVersion=<resolved version>; fail loudly if it is missing so a
// clean-room run can never silently resolve the wrong (or no) artifact.
val pagmoIpoptVersion: String =
    (findProperty("pagmoIpoptVersion") as String?)
        ?: error("Set -PpagmoIpoptVersion=<version> (the published pagmonet4j-ipopt version to test).")

dependencies {
    implementation("io.github.samthegliderpilot:pagmonet4j-ipopt:$pagmoIpoptVersion")
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
