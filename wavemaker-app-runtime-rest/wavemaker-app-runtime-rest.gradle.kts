plugins {
    `java-library-maven-publish`
}

group ="ai.wavemaker.runtime"

dependencies {
    implementation(enforcedPlatform(appDependenciesLibs.boms.slf4j.get().toString()))
    implementation(enforcedPlatform(appDependenciesLibs.boms.springFramework.get().toString()))
    implementation(enforcedPlatform(appDependenciesLibs.boms.jackson.get().toString()))
    implementation(projects.wavemakerCommonsUtil)
    implementation(projects.wavemakerAppRuntimeCommons)
    implementation(projects.wavemakerToolsApidocsCore)
    implementation(projects.wavemakerAppModels)
    implementation(appDependenciesLibs.slf4j.api)
    implementation(appDependenciesLibs.commons.collections4)
    implementation(appDependenciesLibs.commons.io)
    implementation(appDependenciesLibs.commons.lang3)
    implementation(appDependenciesLibs.commons.text)
    implementation(appDependenciesLibs.spring.context)
    implementation(appDependenciesLibs.spring.web)
    implementation(appDependenciesLibs.spring.webmvc)
    implementation(appDependenciesLibs.jackson.annotations)
    implementation(appDependenciesLibs.apache.httpclient)
    implementation(appDependenciesLibs.jakarta.annotation.api)
    implementation(appDependenciesLibs.jakarta.validationApi)
    implementation(appDependenciesLibs.feign.core)
    compileOnly(appDependenciesLibs.jakarta.servlet.api)
    testImplementation(appDependenciesLibs.test.mockito.core)
    testImplementation(appDependenciesLibs.jakarta.servlet.api)
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit.get())
        }
    }
}

javaLibraryMavenPublish {
    scmUrl="git:https://github.com/wavemaker/wavemaker-app-runtime-services.git"
}