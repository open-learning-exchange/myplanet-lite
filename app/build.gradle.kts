import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
}

val mockitoAgent = configurations.create("mockitoAgent") {
    isTransitive = false
}
val mockkAgent = configurations.create("mockkAgent")
val sharedTestImplementation = configurations.create("sharedTestImplementation") {
    isCanBeConsumed = false
    isCanBeResolved = false
}

configurations.named("testImplementation") {
    extendsFrom(sharedTestImplementation)
}

configurations.named("androidTestImplementation") {
    extendsFrom(sharedTestImplementation)
}

android {
    namespace = "org.ole.planet.myplanet.lite"
    compileSdk = 37
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    defaultConfig {
        applicationId = "org.ole.planet.myplanet.lite"
        minSdk = 28
        targetSdk = 36
        versionCode = 457
        versionName = "0.4.57"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PLANET_BASE_URL", "\"http://10.82.1.30/\"")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    jvmArgs("-Xshare:off")
    if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
        jvmArgs(
            "-XX:+EnableDynamicAgentLoading",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.net=ALL-UNNAMED",
            "--add-opens=java.base/java.nio=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
            "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
        )
    }
    systemProperty("robolectric.logging", "none")
    systemProperty("robolectric.logging.enabled", "false")
    // Fix for JDK 25: avoid instrumenting system classes that ASM 9.x cannot read (version 69)
    systemProperty("robolectric.instrumentPackage.include", "org.ole.planet.myplanet.lite")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // implementation
    // AndroidX
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.swiperefreshlayout)

    // Google
    implementation(libs.language.id)
    implementation(libs.material)

    // networking
    implementation(libs.converter.moshi)
    implementation(libs.logging.interceptor)
    implementation(libs.moshi.kotlin)
    implementation(libs.retrofit)

    // UI/media
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.circleimageview)
    implementation(libs.core)
    implementation(libs.ext.tables)
    implementation(libs.markwon.html)
    implementation(libs.markwon.image.glide)
    implementation(libs.glide)
    implementation(libs.photoview)
    implementation(libs.ucrop)

    // utilities
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.worldcountrydata)

    // special agents
    mockkAgent(libs.byte.buddy)
    mockkAgent(libs.byte.buddy.agent)
    mockitoAgent(libs.mockito.core)

    // testImplementation
    // testing
    debugImplementation(libs.androidx.fragment.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.json)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)

    // androidTestImplementation
    // testing
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)

    // shared testing
    sharedTestImplementation(libs.androidx.junit)
    sharedTestImplementation(libs.core.ktx)
    sharedTestImplementation(libs.kotlinx.coroutines.test)
    sharedTestImplementation(libs.mockwebserver)

    // Force consistent versions
    configurations.all {
        resolutionStrategy {
            force(
                libs.androidx.test.core,
                libs.androidx.test.monitor,
                libs.androidx.test.runner,
                libs.androidx.espresso.core,
                libs.androidx.espresso.intents,
                libs.androidx.junit,
                libs.core.ktx
            )
        }
    }
}
