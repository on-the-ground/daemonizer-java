plugins {
    java
    `maven-publish`
    signing
}

group = "io.github.joohyung-park"
version = "0.1.1"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("daemonizer")
                description.set("A bounded-queue daemon for sequential, backpressure-aware event processing in Java.")
                url.set("https://github.com/joohyung-park/daemonizer")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("joohyung-park")
                        name.set("Joohyung Park")
                        email.set("gcjoohyung@naver.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/joohyung-park/daemonizer.git")
                    developerConnection.set("scm:git:ssh://github.com/joohyung-park/daemonizer.git")
                    url.set("https://github.com/joohyung-park/daemonizer")
                }
            }
        }
    }

    repositories {
        maven {
            name = "stagingDeploy"
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["maven"])
}

tasks.register<Zip>("bundleForMavenCentral") {
    dependsOn("publishMavenPublicationToStagingDeployRepository")
    from(layout.buildDirectory.dir("staging-deploy"))
    archiveFileName.set("bundle.zip")
    destinationDirectory.set(layout.buildDirectory.dir("bundle"))
}
