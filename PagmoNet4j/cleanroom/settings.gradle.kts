// Standalone build: invoked with `gradlew -p cleanroom`, it deliberately does NOT see
// the parent PagmoNet4j build. It resolves pagmonet4j only from the local file repo
// produced by the publish workflow, proving the published-shaped base artifact works
// with no dev tools, no submodules, no native env hints.
rootProject.name = "pagmonet4j-cleanroom"
