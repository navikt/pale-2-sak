import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.6.0"
val jacksonVersion = "2.13.2"
val kafkaVersion = "2.8.0"
val kluentVersion = "1.68"
val ktorVersion = "1.6.8"
val logstashLogbackEncoder = "7.0.1"
val logbackVersion = "1.2.11"
val prometheusVersion = "0.15.0"
val junitPlatformLauncher = "1.6.0"
val pale2CommonVersion = "1.b61e3d4"
val junitVersion = "5.8.2"
val ioMockVersion = "1.12.3"
val kotlinVersion = "1.6.0"
val googleCloudStorageVersion = "2.4.5"
val pdfboxVersion = "2.0.24"
val jacksonPatchVersion = "2.13.2.1"
val jacksonBomVersion = "2.13.2.20220324"

plugins {
    kotlin("jvm") version "1.6.0"
    id("org.jmailen.kotlinter") version "3.6.0"
    id("com.diffplug.spotless") version "5.16.0"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/pale-2-common")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-json:$ktorVersion")
    implementation("io.ktor:ktor-client-auth-basic:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson:jackson-bom:$jacksonBomVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonPatchVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoder")

    implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")
    implementation("com.google.cloud:google-cloud-storage:$googleCloudStorageVersion")
    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")

    implementation("no.nav.syfo:pale-2-common-models:$pale2CommonVersion")
    implementation("no.nav.syfo:pale-2-common-networking:$pale2CommonVersion")
    implementation("no.nav.syfo:pale-2-common-kafka:$pale2CommonVersion")

    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation ("io.mockk:mockk:$ioMockVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

}


tasks {
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }

    withType<ShadowJar> {
        transform(ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
    }

    withType<Test> {
        useJUnit()
        testLogging {
            showStandardStreams = true
        }
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    "check" {
        dependsOn("formatKotlin")
    }
}
