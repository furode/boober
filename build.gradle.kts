plugins {
    id("java")
    id("no.skatteetaten.gradle.aurora") version "4.1.1"
}

aurora {
    useAuroraDefaults
    useKotlin {
        useKtLint
    }
    useSpringBoot {
        useCloudContract
    }
    features {
        checkstylePlugin = false
    }
    versions {
        springCloudContract = "2.2.5.RELEASE"
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.4.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")

    implementation("org.eclipse.jgit:org.eclipse.jgit:5.10.0.202012080955-r")
    implementation("org.apache.httpcomponents:httpclient:4.5.13")
    implementation("com.github.fge:json-patch:1.13")

    implementation("org.encryptor4j:encryptor4j:0.1.2")
    // The above library uses an vulnerable bcprov, set the fixed version here, hopefully this will work.
    // pr is sent to maintainer
    implementation("org.bouncycastle:bcprov-jdk15on:1.68")

    implementation("com.github.ben-manes.caffeine:caffeine:2.8.8")
    implementation("org.apache.commons:commons-text:1.9")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fkorotkov:kubernetes-dsl:2.8.1")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.retry:spring-retry")
    implementation("com.cronutils:cron-utils:9.1.3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.mockk:mockk:1.10.4")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.23")
    testImplementation("no.skatteetaten.aurora:mockmvc-extensions-kotlin:1.1.6")
    testImplementation("com.ninja-squad:springmockk:2.0.3")
}
