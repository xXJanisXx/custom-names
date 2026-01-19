plugins {
    alias(libs.plugins.run.paper)
    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.sonatype.central.portal.publisher)
}

dependencies {
    implementation(rootProject.libs.reflection.remapper)
    api(project(":custom-names-api"))
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
}

tasks {
    runServer {
        minecraftVersion("1.21.11")
    }
}