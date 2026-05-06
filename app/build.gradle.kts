import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
}

val mockitoAgent by configurations.creating {
    isTransitive = false
}
val mockkAgent: Configuration by configurations.creating

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
        versionCode = 309
        versionName = "0.3.9"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.withType<Test>().configureEach {
    jvmArgs("-Xshare:off")
    systemProperty("robolectric.logging", "none")
    systemProperty("robolectric.logging.enabled", "false")

    doFirst {
        val agentFile = mockkAgent.find { it.name.startsWith("byte-buddy-agent") }
        if (agentFile != null) {
            jvmArgs("-javaagent:$agentFile")
        }
        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
            jvmArgs("-XX:+EnableDynamicAgentLoading")
        }
        jvmArgs("-javaagent:${mockitoAgent.singleFile}")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
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
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core)
    debugImplementation(libs.androidx.fragment.testing)
    testImplementation(libs.core.ktx)
    testImplementation(libs.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockk)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.fragment.testing)

    // androidTestImplementation
    // testing
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.core.ktx)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockwebserver)

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
