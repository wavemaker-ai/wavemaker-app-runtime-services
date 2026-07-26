plugins {
    `java-library-maven-publish`
}

group ="ai.wavemaker.runtime"

dependencies {
    implementation(enforcedPlatform(appDependenciesLibs.boms.slf4j.get().toString()))
    implementation(enforcedPlatform(appDependenciesLibs.boms.springFramework.get().toString()))
    implementation(projects.wavemakerCommonsUtil)
    implementation(appDependenciesLibs.slf4j.api)
    implementation(appDependenciesLibs.commons.lang3)
    implementation(appDependenciesLibs.commons.collections4)
    implementation(appDependenciesLibs.spring.webmvc)
    compileOnly(appDependenciesLibs.jakarta.servlet.api)
    testImplementation(appDependenciesLibs.test.spring.test)
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit.get())
        }
    }
}

javaLibraryMavenPublish {
    scmUrl="git:https://github.com/wavemaker/wavemaker-app-runtime-services.git"
}