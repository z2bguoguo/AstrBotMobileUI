import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("com.diffplug.spotless")
}
android {
    namespace = "io.github.astrbot.mobile"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.astrbot.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("BUILD_NUMBER") ?: "4").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.3.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            val signingPath = System.getenv("SIGNING_STORE_FILE")
            if (signingPath != null) {
                storeFile = file(signingPath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") { signingConfig = signingConfigs.getByName("release") }
    }
    compileOptions { isCoreLibraryDesugaringEnabled = true; sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint { abortOnError = true; checkReleaseBuilds = true }
    sourceSets.getByName("test").resources.srcDir("src/main/assets")
    testOptions { unitTests.isReturnDefaultValues = false }
}
dependencies {
    implementation("org.mozilla:rhino:1.7.15")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250107")
}
spotless { java { target("src/**/*.java"); googleJavaFormat("1.25.2") } }
tasks.register("verifyReleaseSigning") {
    doLast {
        listOf("SIGNING_STORE_FILE", "SIGNING_STORE_PASSWORD", "SIGNING_KEY_ALIAS", "SIGNING_KEY_PASSWORD").forEach {
            require(!System.getenv(it).isNullOrBlank()) { "Missing persistent release signing configuration: $it" }
        }
    }
}
tasks.matching { it.name == "packageRelease" }.configureEach { dependsOn("verifyReleaseSigning") }

// Every native/bridge change produces a new architecture identity. Script/data edits do not.
val architectureDigest = MessageDigest.getInstance("SHA-256")
val architectureFiles = fileTree("src/main") {
    include("java/**", "res/**", "AndroidManifest.xml")
}.files.sortedBy { it.relativeTo(projectDir).invariantSeparatorsPath } +
    listOf(file("build.gradle.kts"), rootProject.file("build.gradle.kts"), rootProject.file("gradle/libs.versions.toml"), rootProject.file("settings.gradle.kts"), rootProject.file("gradle/wrapper/gradle-wrapper.properties")).filter { it.exists() }
architectureFiles.forEach {
    architectureDigest.update(it.relativeTo(rootProject.projectDir).invariantSeparatorsPath.toByteArray())
    architectureDigest.update(it.readBytes())
}
val architectureId = architectureDigest.digest().joinToString("") { "%02x".format(it) }
android {
    bundle { language { enableSplit = false } }
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "ARCHITECTURE", "\"$architectureId\"")
        buildConfigField("long", "CORE_VERSION", "${System.getenv("BUILD_NUMBER") ?: "4"}L")
    }
}
tasks.register("writeCoreMetadata") {
    doLast {
        val target = layout.buildDirectory.file("core/metadata.json").get().asFile
        target.parentFile.mkdirs()
        target.writeText("""{"abi":1,"architecture":"$architectureId","version":${System.getenv("BUILD_NUMBER") ?: "4"}}""")
    }
}
