import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.betcoin"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.betcoin"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("prod") {
            dimension = "environment"
            applicationId = "com.betcoin"
            buildConfigField("String", "DB_NAME", "\"betcoin.db\"")
        }
        create("dev") {
            dimension = "environment"
            applicationId = "com.betcoin.dev"
            buildConfigField("String", "DB_NAME", "\"betcoin_dev.db\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        jvmToolchain(11)
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        // Export the checked-in Room schemas into the androidTest assets so
        // MigrationTestHelper can load them (spec §3 Schema Versioning).
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }
}

ksp {
    // Exported Room schemas are checked in (spec §3 Schema Versioning).
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Room's migration bundle (room-migration, used by MigrationTestHelper) is compiled against
// kotlinx-serialization 1.8.1, but navigation transitively pins the family to 1.7.3. That skew
// throws AbstractMethodError when the schema JSON is deserialized, so align the whole family.
val kotlinxSerialization = libs.versions.kotlinxSerialization.get()
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlinx" &&
            requested.name.startsWith("kotlinx-serialization")
        ) {
            useVersion(kotlinxSerialization)
        }
    }
}

// Passes each variant's merged manifest to its unit tests, so the dev flavor's
// tests can assert backup stays off (DevManifestBackupTest).
class MergedManifestArgument(
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val manifest: Provider<RegularFile>,
) : CommandLineArgumentProvider {
    override fun asArguments() =
        listOf("-Dbetcoin.mergedManifest=${manifest.get().asFile.absolutePath}")
}

androidComponents {
    onVariants { variant ->
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val testTaskName = "test${variant.name.replaceFirstChar(Char::uppercase)}UnitTest"
        tasks.withType<Test>().named { it == testTaskName }.configureEach {
            jvmArgumentProviders.add(MergedManifestArgument(manifest))
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.runtime)
    debugImplementation(libs.compose.ui.tooling)

    // Activity Compose
    implementation(libs.activity.compose)

    // Lifecycle / ViewModel
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // PIN hashing
    implementation(libs.jbcrypt)

    // Profile pictures
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockito.core)
    testImplementation(libs.kotlinx.coroutines.test)

    // Compose UI tests
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
