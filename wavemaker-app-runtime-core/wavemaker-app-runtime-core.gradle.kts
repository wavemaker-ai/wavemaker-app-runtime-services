plugins {
    `java-library-maven-publish`
}

group ="ai.wavemaker.runtime"

val loggingCapabilityConfiguration = configurations.create("loggingCapabilityConfiguration")
val runtimeLibDependencies = configurations.create("runtimeLibDependencies") {
    extendsFrom(loggingCapabilityConfiguration)
}

// Lists the jars the app runtime already provides (app-runtime-dependencies.txt), so Studio does not copy them again
// into a project. runtime-db is not a dependency of this module anymore, but the shared runtime lib still carries it
// with its libraries (hibernate etc), so it is listed as well.
val appRuntimeDependencies = configurations.create("appRuntimeDependencies") {
    isCanBeConsumed = false
    isCanBeResolved = true
    extendsFrom(configurations.implementation.get(), configurations.runtimeOnly.get())
}

dependencies {
    appRuntimeDependencies(projects.wavemakerAppRuntimeDb)
    implementation(enforcedPlatform(appDependenciesLibs.boms.slf4j.get().toString()))
    implementation(enforcedPlatform(appDependenciesLibs.boms.springFramework.get().toString()))
    implementation(enforcedPlatform(appDependenciesLibs.boms.springSecurity.get().toString()))
    implementation(enforcedPlatform(appDependenciesLibs.boms.springData.get().toString()))
    implementation(enforcedPlatform(appDependenciesLibs.boms.springSession.get().toString()))
    implementation(enforcedPlatform(appDependenciesLibs.boms.log4j.get().toString()))
    implementation(enforcedPlatform(appDependenciesLibs.boms.jackson.get().toString()))
    implementation(projects.wavemakerCommonsUtil)
    implementation(projects.wavemakerAppRuntimeCommons)
    implementation(projects.wavemakerAppRuntimePrefab)
    implementation(projects.wavemakerAppRuntimeRest)
    implementation(projects.wavemakerAppRuntimeDbApi)
    implementation(projects.wavemakerAppRuntimeConnectorAppIntegration)
    implementation(projects.wavemakerToolsApidocsCore)
    implementation(projects.wavemakerAppModels)
    implementation(projects.wavemakerAppMemoryManagement)
    implementation(appDependenciesLibs.commons.collections4)
    implementation(appDependenciesLibs.commons.text)
    implementation(appDependenciesLibs.guava)
    implementation(appDependenciesLibs.spring.boot)
    implementation(appDependenciesLibs.spring.webmvc)
    implementation(appDependenciesLibs.spring.data.jpa)
    implementation(appDependenciesLibs.spring.security.config)
    implementation(appDependenciesLibs.spring.security.core)
    implementation(appDependenciesLibs.spring.security.web)
    implementation(appDependenciesLibs.spring.session.core)
    implementation(appDependenciesLibs.hibernate.validator)
    implementation(appDependenciesLibs.jackson.dataformat.yaml)
    implementation(appDependenciesLibs.jackson.datatype.jsr310)
    implementation(appDependenciesLibs.apache.httpclient)
    implementation(appDependenciesLibs.tika.core)
    compileOnly(appDependenciesLibs.opensaml.saml.impl)
    compileOnly(appDependenciesLibs.opensaml.core)
    compileOnly(appDependenciesLibs.opensaml.api)
    implementation(appDependenciesLibs.antisamy) {
        exclude("org.slf4j", "slf4j-simple")
    }
    implementation(appDependenciesLibs.wordnik.swagger.annotations)
    implementation(appDependenciesLibs.jakarta.annotation.api)
    compileOnly(appDependenciesLibs.jakarta.servlet.api)
    testRuntimeOnly(appDependenciesLibs.hibernate.core) {
        because(
            "WMObjectMapper in runtime-commons still registers the Hibernate jackson module, which needs " +
                    "hibernate-core; this was coming transitively from runtime-db. TODO remove once WMObjectMapper " +
                    "no longer depends on it"
        )
    }
    compileOnly(appDependenciesLibs.spring.contextSupport)
    compileOnly(appDependenciesLibs.spring.security.cas)
    compileOnly(appDependenciesLibs.spring.security.ldap)
    compileOnly(appDependenciesLibs.spring.security.oauth2.client)
    compileOnly(appDependenciesLibs.spring.security.oauth2.jose)
    compileOnly(appDependenciesLibs.spring.security.oauth2.resource.server)
    compileOnly(appDependenciesLibs.spring.security.saml2.service.provider)
    compileOnly(appDependenciesLibs.spring.session.jdbc)
    compileOnly(appDependenciesLibs.mongodb.driver.sync)
    compileOnly(appDependenciesLibs.spring.session.data.mongodb)
    compileOnly(appDependenciesLibs.spring.session.data.redis)
    implementation(appDependenciesLibs.jakarta.xml.bind.api) {
        because("Used for getting jakarta xml bind packages, but previously javax bind api used to be loaded in app runtime core")
    }
    //Logging related dependencies
    implementation(appDependenciesLibs.slf4j.api)
    implementation(appDependenciesLibs.log4j.core)
    loggingCapabilityConfiguration(appDependenciesLibs.log4j.slf4j.impl)

    //runtime dependencies lib
    runtimeLibDependencies(projects.wavemakerAppRuntimeCore)
}

tasks {
    processResources {
        inputs.files(appRuntimeDependencies)
        doLast {
            var content = ""
            appRuntimeDependencies.resolvedConfiguration.resolvedArtifacts.forEach {
                content += it.moduleVersion.toString() + "\n"
            }
            val resourcesDir: File = sourceSets.main.get().output.resourcesDir!!
            resourcesDir.mkdirs()
            File(resourcesDir, "app-runtime-dependencies.txt").writeText(content)
        }
        // Added these below task dependencies as the processResources depends on those too be finished first
        // TODO need to remove the generation of app-runtime-dependencies.txt file, need to solve the use case in a better way
        dependsOn(":wavemaker-commons-util:jar")
        dependsOn(":wavemaker-app-models:jar")
        dependsOn(":wavemaker-app-runtime-commons:jar")
        dependsOn(":wavemaker-app-runtime-prefab:jar")
        dependsOn(":wavemaker-app-runtime-db-api:jar")
        dependsOn(":wavemaker-app-runtime-rest:jar")
        dependsOn(":wavemaker-tools-apidocs-core:jar")
        dependsOn(":wavemaker-app-runtime-connector-app-integration:jar")
        dependsOn(":wavemaker-app-runtime-connector-api:jar")
        dependsOn(":wavemaker-app-memory-management:jar")
    }
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
    scopeMapping.get("compile")?.add(loggingCapabilityConfiguration.name)
}