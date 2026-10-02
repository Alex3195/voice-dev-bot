plugins {
	java
	jacoco
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.diffplug.spotless") version "8.10.3"
}

group = "com.alex"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

val telegramBotsVersion = "10.3.0"
val archUnitVersion = "1.5.1"
val wireMockVersion = "3.13.2"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.telegram:telegrambots-longpolling:$telegramBotsVersion")
	implementation("org.telegram:telegrambots-client:$telegramBotsVersion")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("com.tngtech.archunit:archunit-junit5:$archUnitVersion")
	testImplementation("org.wiremock:wiremock-standalone:$wireMockVersion")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
	finalizedBy(tasks.jacocoTestReport)
}

val coverageExcludes = listOf(
	"**/VoiceDevBotApplication.class",
	"**/config/**",
)

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	classDirectories.setFrom(files(classDirectories.files.map { fileTree(it) { exclude(coverageExcludes) } }))
	reports {
		xml.required = true
		html.required = true
	}
}

tasks.jacocoTestCoverageVerification {
	dependsOn(tasks.test)
	classDirectories.setFrom(files(classDirectories.files.map { fileTree(it) { exclude(coverageExcludes) } }))
	violationRules {
		rule {
			limit {
				counter = "LINE"
				minimum = "0.80".toBigDecimal()
			}
		}
	}
}

tasks.check {
	dependsOn(tasks.jacocoTestCoverageVerification)
}

spotless {
	java {
		googleJavaFormat()
		removeUnusedImports()
		trimTrailingWhitespace()
		endWithNewline()
	}
	kotlinGradle {
		target("*.gradle.kts")
		trimTrailingWhitespace()
		endWithNewline()
	}
}
