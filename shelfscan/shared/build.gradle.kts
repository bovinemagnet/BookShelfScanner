import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

// Static analysis runs in baseline mode: existing findings are recorded in
// detekt-baseline.xml / ktlint-baseline.xml so only new issues fail the build.
detekt {
    buildUponDefaultConfig = true
    baseline = file("detekt-baseline.xml")
}

// In a KMP module the plain `detekt` task (the one wired into `check`) has no
// default sources; point it and the baseline task at the whole src tree so all
// targets' Kotlin sources are analysed without type resolution.
tasks.named<dev.detekt.gradle.Detekt>("detekt") {
    setSource(files("src"))
}
tasks.named<dev.detekt.gradle.DetektCreateBaselineTask>("detektBaseline") {
    setSource(files("src"))
}

ktlint {
    baseline = file("ktlint-baseline.xml")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }

    val xcf = XCFramework("ShelfScanShared")
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "ShelfScanShared"
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test.junit)
            }
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
