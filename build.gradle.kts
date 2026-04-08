import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion="1.10.2"
val jacksonVersion="2.20.1"
val kafkaVersion="3.9.1"
val ktorVersion="3.3.1"
val logstashLogbackEncoder="9.0"
val logbackVersion = "1.5.26"
val prometheusVersion="0.16.0"
val junitVersion="6.0.1"
val ioMockVersion="1.14.6"
val kotlinVersion="2.2.21"
val googleCloudStorageVersion = "2.62.1"
val pdfboxVersion="2.0.35"
val commonsCodecVersion="1.19.0"
val ktfmtVersion="0.44"


val javaVersion = JvmTarget.JVM_21
val otelAnnotationsVersion = "2.21.0"
val otelVersion = "1.56.0"
val typstVersion = "0.14.2"

plugins {
    id("application")
    kotlin("jvm") version "2.2.21"
    id("com.diffplug.spotless") version "8.0.0"
}

application {
    mainClass.set("no.nav.syfo.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}


repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.opentelemetry:opentelemetry-api:${otelVersion}")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:${otelAnnotationsVersion}")
    constraints {
        implementation("commons-codec:commons-codec:$commonsCodecVersion") {
            because("override transient from io.ktor:ktor-client-apache")
        }
    }
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoder")

    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")

    implementation("com.google.cloud:google-cloud-storage:$googleCloudStorageVersion")
    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")

    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation("io.mockk:mockk:$ioMockVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        jvmTarget = javaVersion
    }
}


val downloadTypst by tasks.registering(Exec::class) {
    val typstFile = layout.buildDirectory.file("typst/typst").get().asFile
    outputs.file(typstFile)
    onlyIf { !typstFile.exists() }
    commandLine(
        "bash", "-c",
        "mkdir -p '${typstFile.parentFile.absolutePath}' && " +
            "wget -q 'https://github.com/typst/typst/releases/download/v${typstVersion}/typst-x86_64-unknown-linux-musl.tar.xz'" +
            " -O /tmp/typst-dl.tar.xz && " +
            "tar -xf /tmp/typst-dl.tar.xz --strip-components=1 -C '${typstFile.parentFile.absolutePath}'" +
            " typst-x86_64-unknown-linux-musl/typst && " +
            "chmod +x '${typstFile.absolutePath}' && " +
            "rm -f /tmp/typst-dl.tar.xz",
    )
}

tasks {
    test {
        dependsOn(downloadTypst)
        systemProperty("typst.binary.path", layout.buildDirectory.file("typst/typst").get().asFile.absolutePath)
        systemProperty("project.dir", rootDir.absolutePath)
        useJUnitPlatform()
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }


    spotless {
        kotlin { ktfmt(ktfmtVersion).kotlinlangStyle() }
        check {
            dependsOn("spotlessApply")
        }
    }
}
