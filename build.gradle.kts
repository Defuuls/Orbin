// Top-level build file. Convention plugins live in `build-logic` and are applied per-module.
// Apply security overrides to the plugin classpath before the `plugins` block resolves.
buildscript {
    configurations.classpath {
        resolutionStrategy.eachDependency {
            when {
                requested.group == "io.netty" && requested.name.startsWith("netty-") -> {
                    useVersion("4.1.135.Final")
                    because("Dependabot reports multiple Netty CVEs in the Gradle plugin classpath.")
                }

                requested.group == "org.bouncycastle" && requested.name in setOf(
                    "bcprov-jdk18on",
                    "bcpkix-jdk18on",
                    "bcutil-jdk18on",
                    "bcpg-jdk18on",
                ) -> {
                    useVersion("1.84")
                    because("Dependabot reports Bouncy Castle CVEs in the Gradle plugin classpath.")
                }

                requested.group == "org.apache.commons" && requested.name == "commons-compress" -> {
                    useVersion("1.28.0")
                    because("Dependabot reports Apache Commons Compress CVEs in the Gradle plugin classpath.")
                }

                requested.group == "org.apache.commons" && requested.name == "commons-io" -> {
                    useVersion("2.18.0")
                    because("Dependabot reports an Apache Commons IO denial-of-service vulnerability.")
                }

                requested.group == "org.jdom" && requested.name == "jdom2" -> {
                    useVersion("2.0.6.1")
                    because("Dependabot reports a JDOM XXE vulnerability in the Gradle plugin classpath.")
                }

                requested.group == "com.google.protobuf" && requested.name.startsWith("protobuf-") -> {
                    useVersion("3.25.5")
                    because("Dependabot reports protobuf denial-of-service vulnerabilities.")
                }

                requested.group == "org.bitbucket.b_c" && requested.name == "jose4j" -> {
                    useVersion("0.9.6")
                    because("Dependabot reports a jose4j denial-of-service vulnerability.")
                }

                requested.group == "ch.qos.logback" && requested.name.startsWith("logback-") -> {
                    useVersion("1.5.35")
                    because("Dependabot reports Logback deserialization and object-injection vulnerabilities.")
                }

                requested.group == "org.apache.httpcomponents" && requested.name == "httpclient" -> {
                    useVersion("4.5.13")
                    because("Dependabot reports an Apache HttpClient XSS vulnerability (GHSA-7r82-7xv7-xcpj).")
                }

                requested.group == "io.opentelemetry" && requested.name in setOf(
                    "opentelemetry-api",
                    "opentelemetry-extension-trace-propagators",
                ) -> {
                    useVersion("1.62.0")
                    because(
                        "Dependabot reports an OpenTelemetry unbounded memory allocation " +
                            "vulnerability (GHSA-rcgg-9c38-7xpx).",
                    )
                }
            }
        }
    }
}

// Plugins are declared here with `apply false` so their classpath is available to subprojects.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.roborazzi) apply false
}

fun ResolutionStrategy.applySecurityDependencyPatches() {
    eachDependency {
        when {
            requested.group == "io.netty" && requested.name.startsWith("netty-") -> {
                useVersion("4.1.135.Final")
                because("Dependabot reports multiple Netty CVEs in the Android Gradle Plugin transitive classpath.")
            }

            requested.group == "org.bouncycastle" && requested.name in setOf(
                "bcprov-jdk18on",
                "bcpkix-jdk18on",
                "bcutil-jdk18on",
                "bcpg-jdk18on",
            ) -> {
                useVersion("1.84")
                because("Dependabot reports Bouncy Castle CVEs in the Android Gradle Plugin transitive classpath.")
            }

            requested.group == "org.apache.commons" && requested.name == "commons-compress" -> {
                useVersion("1.28.0")
                because("Dependabot reports Apache Commons Compress CVEs in the Android Gradle Plugin transitive classpath.")
            }

            requested.group == "org.apache.commons" && requested.name == "commons-io" -> {
                useVersion("2.18.0")
                because("Dependabot reports an Apache Commons IO denial-of-service vulnerability.")
            }

            requested.group == "org.jdom" && requested.name == "jdom2" -> {
                useVersion("2.0.6.1")
                because("Dependabot reports a JDOM XXE vulnerability in the Android Gradle Plugin transitive classpath.")
            }

            requested.group == "com.google.protobuf" && requested.name.startsWith("protobuf-") -> {
                useVersion("3.25.5")
                because("Dependabot reports protobuf denial-of-service vulnerabilities.")
            }

            requested.group == "org.bitbucket.b_c" && requested.name == "jose4j" -> {
                useVersion("0.9.6")
                because("Dependabot reports a jose4j denial-of-service vulnerability.")
            }

            requested.group == "ch.qos.logback" && requested.name.startsWith("logback-") -> {
                useVersion("1.5.35")
                because("Dependabot reports Logback deserialization and object-injection vulnerabilities.")
            }

            requested.group == "org.apache.httpcomponents" && requested.name == "httpclient" -> {
                useVersion("4.5.13")
                because("Dependabot reports an Apache HttpClient XSS vulnerability (GHSA-7r82-7xv7-xcpj).")
            }

            requested.group == "io.opentelemetry" && requested.name in setOf(
                "opentelemetry-api",
                "opentelemetry-extension-trace-propagators",
            ) -> {
                useVersion("1.62.0")
                because(
                    "Dependabot reports an OpenTelemetry unbounded memory allocation " +
                        "vulnerability (GHSA-rcgg-9c38-7xpx).",
                )
            }
        }
    }
}

configurations.configureEach {
    resolutionStrategy.applySecurityDependencyPatches()
}

// Apply code-quality tooling uniformly. Centralizing it here keeps per-module build files thin
// and guarantees the `detekt` / `ktlintCheck` tasks exist on every module for CI.
subprojects {
    configurations.configureEach {
        resolutionStrategy.applySecurityDependencyPatches()
    }

    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        basePath = rootProject.projectDir.absolutePath
    }

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.4.1")
        android.set(true)
        ignoreFailures.set(false)
        filter {
            // Exclude generated sources.
            exclude { it.file.path.contains("/build/") }
        }
    }

    // detekt should analyze with type resolution where possible and not fail the whole run early.
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            sarif.required.set(true)
            xml.required.set(false)
        }
    }

    // Gradle 9.4+ fails test tasks that discover zero tests. Several feature modules have no
    // unit tests yet, but their unit-test classpaths still contain generated classes, which
    // trips the "test sources present" heuristic. Restore the pre-9.4 behavior.
    tasks.withType<Test>().configureEach {
        failOnNoDiscoveredTests = false
    }
}
