import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.named<ProcessResources>("processResources") {
    from(layout.projectDirectory.file("LICENSE")) {
        into("META-INF")
        rename { "LICENSE_AE2WTX" }
    }
    from(layout.projectDirectory.file("THIRD_PARTY_NOTICES.md")) {
        into("META-INF")
    }
}
