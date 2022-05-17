plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("coverage/build/libs/coverage.jar"))
    testImplementation(libs.bundles.asm)
    testImplementation(libs.quiltflower)
}

testing {
    suites {
        // Configure the built-in test suite
        val test by getting(JvmTestSuite::class) {
            // Use JUnit Jupiter test framework
            useJUnitJupiter("5.8.1")
        }
    }
}
