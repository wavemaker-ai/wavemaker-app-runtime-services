plugins {
    `java-library-maven-publish`
}

group = "ai.wavemaker.runtime"

dependencies {
    implementation(enforcedPlatform(appDependenciesLibs.boms.slf4j.get().toString()))
    implementation(enforcedPlatform(appDependenciesLibs.boms.springFramework.get().toString()))
    implementation(enforcedPlatform(appDependenciesLibs.boms.springData.get().toString()))
    implementation(projects.wavemakerCommonsUtil)
    implementation(projects.wavemakerAppRuntimeCommons)
    api(projects.wavemakerAppRuntimeDbApi)
    implementation(appDependenciesLibs.slf4j.api)
    implementation(appDependenciesLibs.commons.lang3)
    implementation(appDependenciesLibs.spring.core)
    implementation(appDependenciesLibs.spring.beans)
    implementation(appDependenciesLibs.spring.orm)
    implementation(appDependenciesLibs.spring.data.commons)
    implementation(appDependenciesLibs.hibernate.core)
}

javaLibraryMavenPublish {
    scmUrl = "git:https://github.com/wavemaker/wavemaker-app-runtime-services.git"
}
