import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.provider.MapProperty
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val cargoTargets = mapOf(
    "aarch64-linux-android"   to "arm64-v8a",
    "armv7-linux-androideabi" to "armeabi-v7a",
    "x86_64-linux-android"   to "x86_64",
    "i686-linux-android"     to "x86",
)

fun osClassifier(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        os.contains("mac") && (arch.contains("aarch64") || arch.contains("arm")) -> "osx-aarch_64"
        os.contains("mac")   -> "osx-x86_64"
        os.contains("linux") && arch.contains("aarch64") -> "linux-aarch_64"
        os.contains("linux") -> "linux-x86_64"
        os.contains("win")   && arch.contains("amd64")  -> "windows-x86_64"
        os.contains("win")   -> "windows-x86_32"
        else -> error("Unsupported OS/arch for protoc: $os/$arch")
    }
}

abstract class CargoBuildTask : DefaultTask() {
    @get:InputDirectory
    abstract val cargoProjectDir: DirectoryProperty

    @get:OutputDirectory
    abstract val jniLibsDir: DirectoryProperty

    @get:Input
    abstract val cargoPath: Property<String>

    @get:Input
    abstract val ndkHome: Property<String>

    @get:Input
    abstract val envPath: Property<String>

    @get:Input
    abstract val targets: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val rustFlags: Property<String>

    @get:Inject
    abstract val execOps: ExecOperations

    @TaskAction
    fun build() {
        val jniLibs  = jniLibsDir.get().asFile
        val cargoDir = cargoProjectDir.get().asFile

        targets.get().values.forEach { abi -> jniLibs.resolve(abi).mkdirs() }

        targets.get().forEach { (target, _) ->
            execOps.exec {
                workingDir(cargoDir)
                environment("ANDROID_NDK_HOME", ndkHome.get())
                environment("PATH", envPath.get())
                rustFlags.orNull?.takeIf { it.isNotBlank() }
                    ?.let { environment("RUSTFLAGS", it) }
                commandLine(
                    cargoPath.get(),
                    "ndk",
                    "--platform", "24",
                    "--target", target,
                    "-o", jniLibs.absolutePath,
                    "build", "--release",
                )
            }
        }
    }
}

abstract class GenerateProtoTask : DefaultTask() {
    @get:InputFiles
    abstract val protocExecutable: ConfigurableFileCollection

    @get:InputDirectory
    abstract val protoSrcDir: DirectoryProperty

    @get:OutputDirectory
    abstract val javaOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val kotlinOutputDir: DirectoryProperty

    @get:Inject
    abstract val execOps: ExecOperations

    @TaskAction
    fun generate() {
        val protocExe = protocExecutable.singleFile
        protocExe.setExecutable(true)

        val javaOut   = javaOutputDir.get().asFile.also  { it.mkdirs() }
        val kotlinOut = kotlinOutputDir.get().asFile.also { it.mkdirs() }
        val protoDir  = protoSrcDir.get().asFile

        val protoFiles = protoDir.walkTopDown()
            .filter { it.extension == "proto" }
            .map    { it.absolutePath }
            .toList()

        execOps.exec {
            commandLine(
                protocExe.absolutePath,
                "--proto_path=${protoDir.absolutePath}",
                "--java_out=lite:${javaOut.absolutePath}",
                "--kotlin_out=lite:${kotlinOut.absolutePath}",
                *protoFiles.toTypedArray()
            )
        }
    }
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(FileInputStream(file))
}

val protocArtifact by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

android {
    namespace = "io.github.iamlooper.benchsuite"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    (localProperties.getProperty("ndk.version") ?: System.getenv("NDK_VERSION"))
        ?.let { ndkVersion = it }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            val keystoreProperties = Properties()

            // 1. Local development - reads from keystore.properties file
            if (keystorePropertiesFile.exists()) {
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
            // 2. GitHub Actions - reads from repository secrets via environment variables
            else if (System.getenv("ANDROID_KEYSTORE_PASSWORD") != null) {
                storeFile = file("keystore.jks")
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
            // 3. No signing config available - release builds will be unsigned
            else {
                logger.warn("WARNING: Release signing not configured. keystore.properties not found and ANDROID_KEYSTORE_PASSWORD not set.")
            }
        }
    }

    defaultConfig {
        applicationId = "io.github.iamlooper.benchsuite"
        minSdk = 24
        targetSdk = 36
        versionCode = 20
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL",
            "\"${localProperties.getProperty("supabase.url") ?: System.getenv("SUPABASE_URL") ?: ""}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY",
            "\"${localProperties.getProperty("supabase.publishable-key")
                ?: System.getenv("SUPABASE_PUBLISHABLE_KEY")
                ?: ""}\"")
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

// Protobuf
// protobuf-gradle-plugin ≤ 0.9.6 casts ApplicationExtension to the removed BaseExtension on
// AGP 9.x, so we drive protoc directly instead of using the plugin.
val generateProto by tasks.registering(GenerateProtoTask::class) {
    protocExecutable.from(protocArtifact)
    protoSrcDir.set(layout.projectDirectory.dir("src/main/proto"))
    javaOutputDir.set(layout.buildDirectory.dir("generated/source/proto/main/java"))
    kotlinOutputDir.set(layout.buildDirectory.dir("generated/source/proto/main/kotlin"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(
            generateProto,
            GenerateProtoTask::javaOutputDir
        )
        variant.sources.kotlin?.addGeneratedSourceDirectory(
            generateProto,
            GenerateProtoTask::kotlinOutputDir
        )
    }
}

// Cargo-ndk integration

val cargoBuild by tasks.registering(CargoBuildTask::class) {
    group       = "build"
    description = "Build BenchSuite Rust native library via cargo-ndk"

    cargoProjectDir.set(layout.projectDirectory.dir("src/main/rust"))
    jniLibsDir.set(layout.projectDirectory.dir("src/main/jniLibs"))

    val cargoHome = System.getenv("CARGO_HOME")
        ?: localProperties.getProperty("cargo.home")
        ?: error("CARGO_HOME not set and cargo.home not found in local.properties")

    cargoPath.set("$cargoHome/bin/cargo")

    ndkHome.set(
        System.getenv("ANDROID_NDK_HOME") ?: run {
            val sdkDir = localProperties.getProperty("sdk.dir")
                ?: System.getenv("ANDROID_HOME")
                ?: error("sdk.dir not in local.properties and ANDROID_HOME not set")
            val ndkVer = localProperties.getProperty("ndk.version")
                ?: System.getenv("NDK_VERSION")
                ?: error("ndk.version not in local.properties and NDK_VERSION not set")
            "$sdkDir/ndk/$ndkVer"
        }
    )

    envPath.set(
        "${System.getenv("CARGO_HOME") ?: localProperties.getProperty("cargo.home")}/bin:" +
            (System.getenv("PATH") ?: "")
    )

    targets.set(cargoTargets)

    rustFlags.set(
        System.getenv("RUSTFLAGS")
            ?: localProperties.getProperty("rust.flags")
            ?: ""
    )
}

tasks.named("preBuild").configure {
    dependsOn(cargoBuild)
}

dependencies {
    add("protocArtifact", "com.google.protobuf:protoc:${libs.versions.protobuf.get()}:${osClassifier()}@exe")

    // Core library desugaring
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.annotation)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.animation)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Supabase SDK (Edge Functions + PostgREST)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.functions)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Protobuf lite (for generated bridge/command classes)
    implementation(libs.protobuf.javalite)
    implementation(libs.protobuf.kotlin.lite)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
