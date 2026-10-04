import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = "org.icarus"
version = "1.10.0"

val paperApiVersion = "1.21.11-R0.1-SNAPSHOT"
val lombokVersion = "1.18.42"
val jacksonVersion = "2.17.2"
val cloudVersion = "2.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")

    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")

    implementation(platform("org.incendo:cloud-minecraft-bom:$cloudVersion"))
    implementation("org.incendo:cloud-paper")
    implementation("org.incendo:cloud-minecraft-extras") {
        exclude(group = "net.kyori")
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    relocate("com.fasterxml.jackson", "org.icarus.tingere.libs.jackson")
    relocate("org.yaml.snakeyaml", "org.icarus.tingere.libs.snakeyaml")
    relocate("org.incendo", "org.icarus.tingere.libs.cloud")
    exclude("org/immutables/**")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.runServer {
    minecraftVersion("1.21.11")
    jvmArgs("-Dcom.mojang.eula.agree=true")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

