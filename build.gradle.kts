import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Properties
import org.gradle.api.tasks.GradleBuild

buildscript{
    repositories {
        maven {
            url = uri("https://hmrc.bintray.com/mobile-releases")
        }
    }
    dependencies {
        classpath("uk.gov.hmrc.gradle:spotless:0.1.0")
    }
}


/***********************************************************************************************************************
 * Project Gradle Config
 ***********************************************************************************************************************/
apply(plugin = "uk.gov.hmrc.spotless")

group = "uk.gov.hmrc"
description = "Multiplatform Tax Calculator library"
version = System.getenv("BITRISE_GIT_TAG") ?: ("SNAPSHOT-" + getDate())

plugins {
    `maven-publish`
    kotlin("multiplatform").version("1.3.61")
    jacoco
    java
    id("com.github.dawnwords.jacoco.badge").version("0.1.0")
    id("io.gitlab.arturbosch.detekt").version("1.1.1")
    id("com.jfrog.bintray").version("1.8.4")
}

repositories {
    mavenCentral()
    jcenter()
    jcenter {
        url = uri("https://hmrc.bintray.com/releases/")
    }
    maven {
        url = uri("https://plugins.gradle.org/m2/")
    }
}

/***********************************************************************************************************************
 * Declarations
 ***********************************************************************************************************************/

val frameworkName = "TaxKalculator"
val licenseString = """/*
 * Copyright ${getYear()} HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
"""

/***********************************************************************************************************************
 * Kotlin Configuration
 ***********************************************************************************************************************/

kotlin {

    jvm()
    val iosX64 = iosX64("ios")
    val iosArm32 = iosArm32()
    val iosArm64 = iosArm64() //Simulator

    targets{
        configure(listOf(iosX64, iosArm32, iosArm64)) {
            binaries.framework {
                baseName = frameworkName
                embedBitcode("disable")
            }
        }
    }

    sourceSets.all {
        languageSettings.useExperimentalAnnotation("kotlin.Experimental")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }

        val iosMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }

        val iosTest by getting {
        }

        val iosArm32Main by sourceSets.getting
        val iosArm64Main by sourceSets.getting

        configure(listOf(iosArm32Main, iosArm64Main)) {
            dependsOn(iosMain)
        }

        val iosArm32Test by sourceSets.getting
        val iosArm64Test by sourceSets.getting
        configure(listOf(iosArm32Test, iosArm64Test)) {
            dependsOn(iosTest)
        }
    }

    tasks.register("iosTest") {
        group = project.name
        val device = project.findProperty("iosDevice")?.toString() ?: "iPhone 8"
        this.dependsOn(iosX64.binaries.getTest("DEBUG").linkTaskName)
        group = JavaBasePlugin.VERIFICATION_GROUP
        description = "Runs tests for target ios on an iOS simulator"

        doLast {
            val binary = iosArm64.binaries.getTest("DEBUG").outputFile
            exec {
                commandLine(listOf("xcrun", "simctl", "spawn", device, binary.absolutePath))
            }
        }
    }

    tasks.register<org.jetbrains.kotlin.gradle.tasks.FatFrameworkTask>("fatFramework") {
        group = project.name
        destinationDir = File(buildDir, "xcode-frameworks")

        val ios32Framework = iosArm32.binaries.getFramework("RELEASE")
        val ios64Framework = iosX64.binaries.getFramework("RELEASE")
        val iosSimulatorFramework = iosArm64.binaries.getFramework("RELEASE")

        this.dependsOn(ios32Framework.linkTask)
        this.dependsOn(ios64Framework.linkTask)
        this.dependsOn(iosSimulatorFramework.linkTask)

        from(
                ios32Framework,
                ios64Framework,
                iosSimulatorFramework
        )

        doLast {
            File(destinationDir, "gradlew").apply {
                setExecutable(true)
            }
                    .writeText("#!/bin/bash\nexport 'JAVA_HOME=${System.getProperty("java.home")}'\ncd '${rootProject.rootDir}'\n./gradlew \$@\n")
        }
    }
}

tasks.getByName("build").dependsOn(tasks.getByName("fatFramework"))

/***********************************************************************************************************************
 * Other Task Configuration
 ***********************************************************************************************************************/

configurations {
    compileClasspath
}

jacocoBadgeGenSetting {
    jacocoReportPath = "$buildDir/reports/jacoco/testCommonUnitTestCoverage/testCommonUnitTestCoverage.xml"
}

jacoco {
    toolVersion = "0.8.4"
}

detekt {
    input = files("src/commonMain/kotlin")
    config = files("detekt-config.yml")
    reports {
        html {
            enabled = true
            destination = file("build/reports/detekt/index.html")
        }
    }
}

bintray {
    val credentials = Properties()
    rootProject.file("credentials.properties").inputStream().use { credentials.load(it) }

    user = credentials.getProperty("bintray.user")
    key = credentials.getProperty("bintray.apikey")
    setPublications("jvm", "metadata")

    publish = true

    pkg = PackageConfig()
    pkg.repo = "mobile-releases"
    pkg.name = project.name
    pkg.userOrg = "hmrc"
    pkg.desc = project.description
    pkg.setLicenses("Apache-2.0")
    pkg.version.name = project.version.toString()
    pkg.version.released = Date().toString()
}

tasks.jacocoTestCoverageVerification {
    group = project.name

    violationRules {
        rule {
            limit {
                minimum = "0.95".toBigDecimal()
            }
        }
    }
    val excludes = listOf("**/*Test*.*")
    val coverageSourceDirs = listOf("src/commonMain/kotlin")
    sourceDirectories.setFrom(files(coverageSourceDirs))
    classDirectories.setFrom(fileTree("${project.buildDir}/classes/kotlin/jvm/").exclude(excludes))
    executionData.setFrom(files("${project.buildDir}/jacoco/jvmTest.exec"))
}


/***********************************************************************************************************************
 * Custom Functions
 **********************************************************************************************************************/
fun getYear(): String {
    return Calendar.getInstance().get(Calendar.YEAR).toString()
}

fun getDate(): String {
    val date = Date()
    val format = "yyyyMMddHHmm"
    return SimpleDateFormat(format).format(date).toString()
}


/***********************************************************************************************************************
 * Custom Tasks
 ***********************************************************************************************************************/

tasks.register<JacocoReport>("testCommonUnitTestCoverage") {
    group = project.name
    description = "Generate Jacoco coverage reports on the common module build."

    this.dependsOn("allTests")
    val excludes = listOf("**/*Test*.*")
    val coverageSourceDirs = listOf("src/commonMain/kotlin")
    executionData(files("${project.buildDir}/jacoco/jvmTest.exec"))

    reports {
        xml.isEnabled = true
        html.isEnabled = true
        sourceDirectories.setFrom(files(coverageSourceDirs))
        classDirectories.setFrom(fileTree("${project.buildDir}/classes/kotlin/jvm/").exclude(excludes))
    }
}

tasks.register<GradleBuild>("cleanBuildTestCoverage") {
    group = project.name

    tasks = listOf(
            "clean",
            "cleanAllTests",
            "build",
            "testCommonUnitTestCoverage",
            "generateJacocoBadge",
            "jacocoTestCoverageVerification"
    )
}
