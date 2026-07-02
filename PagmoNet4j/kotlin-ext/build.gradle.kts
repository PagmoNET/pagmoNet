plugins {
    kotlin("jvm")
    `maven-publish`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core"))
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.library.path", System.getenv("PAGMO4J_NATIVE_DIR") ?: ".")
}

publishing {
    repositories {
        mavenLocal()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/samthegliderpilot/PagmoNet4j")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            artifactId = "pagmonet4j-kotlin"
            from(components["java"])
            pom {
                name.set("pagmonet4j-kotlin")
                description.set("Kotlin extension functions for pagmonet4j")
                url.set("https://github.com/samthegliderpilot/pagmonet4j")
            }
        }
    }
}
