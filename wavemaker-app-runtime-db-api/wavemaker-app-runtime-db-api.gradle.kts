plugins {
    `java-library-maven-publish`
}

group = "ai.wavemaker.runtime"

dependencies {
    implementation(enforcedPlatform(appDependenciesLibs.boms.springFramework.get().toString()))
    implementation(projects.wavemakerAppRuntimeCommons)
    implementation(appDependenciesLibs.spring.context)
    implementation(appDependenciesLibs.spring.web)
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit.get())
        }
    }
}

javaLibraryMavenPublish {
    scmUrl = "git:https://github.com/wavemaker/wavemaker-app-runtime-services.git"
}
