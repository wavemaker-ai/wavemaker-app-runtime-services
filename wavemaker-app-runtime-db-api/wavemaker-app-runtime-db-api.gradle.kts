plugins {
    `java-library-maven-publish`
}

group = "ai.wavemaker.runtime"

dependencies {
    implementation(enforcedPlatform(appDependenciesLibs.boms.springFramework.get().toString()))
    implementation(enforcedPlatform(appDependenciesLibs.boms.jackson.get().toString()))
    implementation(projects.wavemakerCommonsUtil)
    implementation(projects.wavemakerAppRuntimeCommons)
    implementation(appDependenciesLibs.spring.context)
    implementation(appDependenciesLibs.spring.web)
    implementation(appDependenciesLibs.jackson.annotations)
    implementation(appDependenciesLibs.jakarta.validationApi)
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
