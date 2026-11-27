plugins {
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.4"
    id("org.flywaydb.flyway") version "10.22.0"
    java
}

//java {
//    toolchain {
//        languageVersion.set(JavaLanguageVersion.of(17))
//    }
//}

group = "org.example"
version = "1.0-SNAPSHOT"

object Versions {
    val lombokVersion = "1.18.48"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.flywaydb:flyway-maven-plugin:10.22.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    implementation("org.mapstruct:mapstruct:1.6.3")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    compileOnly("org.projectlombok:lombok:${Versions.lombokVersion}")
    annotationProcessor("org.projectlombok:lombok:${Versions.lombokVersion}")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
    testCompileOnly("org.projectlombok:lombok:${Versions.lombokVersion}")
    testAnnotationProcessor("org.projectlombok:lombok:${Versions.lombokVersion}")

    testImplementation("org.assertj:assertj-core:3.26.3")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // JUnit 5 integration for Testcontainers lifecycle management
    testImplementation("org.testcontainers:junit-jupiter")
    // The specific Testcontainers module you need (PostgreSQL example)
    testImplementation("org.testcontainers:postgresql")

    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    implementation("commons-codec:commons-codec:1.23.0")
}

tasks.test {
    useJUnitPlatform()
}