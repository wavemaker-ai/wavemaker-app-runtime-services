package com.wavemaker.runtime.core.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WMAutoConfigurationImporterTest {

    @Configuration(proxyBeanMethods = false)
    public static class SampleAutoConfiguration {
        @Bean
        public String sampleBean() {
            return "imported";
        }
    }

    @Test
    public void importsTheClassesListedInTheImportsFile() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(WMAutoConfigurationImporter.class)) {
            assertEquals("imported", context.getBean("sampleBean"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class UserConfiguration {
        @Bean
        public String sampleBean() {
            return "user";
        }
    }

    @Test
    public void aBeanOfTheProjectOverridesAnAutoConfiguredBeanWhateverTheRegistrationOrder() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(UserConfiguration.class, WMAutoConfigurationImporter.class);
            context.refresh();
            assertEquals("user", context.getBean("sampleBean"));
        }
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(WMAutoConfigurationImporter.class, UserConfiguration.class);
            context.refresh();
            assertEquals("user", context.getBean("sampleBean"));
        }
    }

    @Test
    public void aBeanDefinedBeforeTheContextRefreshesOverridesAnAutoConfiguredBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("sampleBean", String.class, () -> "defined-first");
            context.register(WMAutoConfigurationImporter.class);
            context.refresh();
            assertEquals("defined-first", context.getBean("sampleBean"));
        }
    }
}
