plugins {
    `java-library-maven-publish`
}

group ="ai.wavemaker.runtime"

dependencies {
    implementation(enforcedPlatform(appDependenciesLibs.boms.springFramework.get().toString()))
    implementation(enforcedPlatform(appDependenciesLibs.boms.springData.get().toString()))
    implementation(enforcedPlatform(appDependenciesLibs.boms.jackson.get().toString()))
    implementation(projects.wavemakerCommonsUtil)
    implementation(projects.wavemakerAppRuntimePrefab)
    implementation(appDependenciesLibs.jakarta.annotation.api)
    implementation(appDependenciesLibs.commons.io)
    implementation(appDependenciesLibs.commons.lang3)
    implementation(appDependenciesLibs.spring.context)
    implementation(appDependenciesLibs.spring.web)
    implementation(appDependenciesLibs.spring.webmvc)
    implementation(appDependenciesLibs.spring.data.commons)
    implementation(appDependenciesLibs.jackson.dataformat.yaml)
    compileOnly(appDependenciesLibs.jakarta.servlet.api)
    testImplementation(appDependenciesLibs.test.jsonassert)
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