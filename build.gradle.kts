    plugins {
        kotlin("jvm") version "2.2.21"
        kotlin("plugin.spring") version "2.2.21"
        kotlin("plugin.jpa") version "2.2.21"
        id("org.springframework.boot") version "4.0.3"
        id("io.spring.dependency-management") version "1.1.7"
        id("io.swagger.core.v3.swagger-gradle-plugin") version "2.2.45"
    }

    group = "nl.fayece"
    version = "0.0.1-SNAPSHOT"
    description = "PaymentProcessing"

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        implementation("org.springframework.boot:spring-boot-starter-data-jpa")
        implementation("org.springframework.boot:spring-boot-starter-flyway")
        implementation("org.springframework.boot:spring-boot-starter-validation")
        implementation("org.springframework.boot:spring-boot-starter-webmvc")
        implementation("org.flywaydb:flyway-database-postgresql")
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.2")
        implementation("tools.jackson.module:jackson-module-kotlin")
        implementation("jakarta.validation:jakarta.validation-api:3.1.1")
        implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
        developmentOnly("org.springframework.boot:spring-boot-devtools")
        developmentOnly("org.springframework.boot:spring-boot-docker-compose")
        runtimeOnly("org.postgresql:postgresql")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
        testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
        testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
        testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
        testImplementation("org.springframework.boot:spring-boot-testcontainers")
        testImplementation("org.testcontainers:junit-jupiter")
        testImplementation("org.testcontainers:testcontainers-postgresql")
        testImplementation("io.mockk:mockk:1.14.6")
        testImplementation("com.ninja-squad:springmockk:5.0.1")
        testImplementation("org.springframework.boot:spring-boot-starter-restclient")
    }

    kotlin {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        }
    }

    allOpen {
        annotation("jakarta.persistence.Entity")
        annotation("jakarta.persistence.MappedSuperclass")
        annotation("jakarta.persistence.Embeddable")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
