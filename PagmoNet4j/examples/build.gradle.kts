plugins {
    java
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
    // pagmonet4j is published to GitHub Packages, which requires authentication even for public
    // packages. Provide a `read:packages` token via ~/.gradle/gradle.properties (gpr.user / gpr.token)
    // or the GITHUB_ACTOR / GITHUB_TOKEN environment variables -- see README.
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/PagmoNET/pagmoNet")
        credentials {
            username = (findProperty("gpr.user") as String?) ?: System.getenv("GITHUB_ACTOR") ?: ""
            password = (findProperty("gpr.token") as String?) ?: System.getenv("GITHUB_TOKEN") ?: ""
        }
    }
}

kotlin {
    jvmToolchain(21)
}

// "+" floats to the newest published version, so this never needs bumping per release.
// Pin it to an explicit version (e.g. "1.0.0") for a reproducible build.
val pagmoVersion = "+"

dependencies {
    // pagmonet4j-kotlin transitively pulls in the base pagmonet4j; pagmonet4j-ipopt adds the native
    // IPOPT runtime (no PAGMO4J_NATIVE_DIR needed -- the published jar carries the native and extracts
    // it at load time). Comment out the -ipopt line to see the graceful "IPOPT not available" path.
    implementation("io.github.pagmonet:pagmonet4j-kotlin:$pagmoVersion")
    implementation("io.github.pagmonet:pagmonet4j-ipopt:$pagmoVersion")
}

application {
    mainClass.set("io.github.pagmonet.pagmonet4j.examples.Main")
}
