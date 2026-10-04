plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "tv.own.owntv.mobile.baselineprofile"
    compileSdk {
        version = release(37)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // androidx.benchmark's prebuilt .so are already stripped, so the strip step only ever
        // printed "Unable to strip …" without changing a byte. Same as :app.
        jniLibs {
            keepDebugSymbols += "**/*.so"
        }
    }

    defaultConfig {
        // Macrobenchmark needs API 28+ to read the compilation state it drives.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // **The one real difference from the TV app's copy.** That module pins `x86_64`, because a
        // baseline profile needs an API 33+ device and the arm TV boxes it targets are older — so it
        // can only record on an emulator. A phone running this app is API 33+ as a matter of course,
        // so recording happens on the real device, against the real arm APK, on the real hardware
        // the profile is for.
        missingDimensionStrategy("abi", "standard")
    }

    targetProjectPath = ":app"
}

// Records on whatever single device is attached. Set `useConnectedDevices = false` and declare a
// managed device here if this ever needs to run unattended — it is not run by CI today.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
