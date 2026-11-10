plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

// Le module compile exactement les mêmes fichiers que l'application Android :
// le pipeline du mot d'appel et la couche réseau sont du Kotlin pur, sans classe Android.
sourceSets {
    main {
        kotlin.srcDir("../app/src/main/java/com/cornelius/satellite/wake")
        kotlin.srcDir("../app/src/main/java/com/cornelius/satellite/net")
        kotlin.srcDir("src/main/kotlin")
    }
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime:1.20.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    implementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    implementation("org.json:json:20240303")
}

application {
    mainClass.set("com.cornelius.satellite.verification.MainKt")
}
