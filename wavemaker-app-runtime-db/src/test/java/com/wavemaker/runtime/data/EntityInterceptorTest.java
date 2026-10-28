package com.wavemaker.runtime.data;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.wavemaker.runtime.commons.file.manager.ExportedFileManager;
import com.wavemaker.runtime.data.dao.WMGenericDao;
import com.wavemaker.runtime.data.regsvc.AuditedWidget;
import com.wavemaker.runtime.data.support.ServerVariables;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The entity interceptor injects the value of a server variable into an annotated field on insert and update.
 */
public class EntityInterceptorTest {

    private static final AtomicReference<String> USER = new AtomicReference<>("creator");

    private AnnotationConfigApplicationContext context;
    private TransactionTemplate tx;
    private WMGenericDao<AuditedWidget, Integer> dao;

    @Configuration
    static class Infra {
        @Bean
        static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        ExportedFileManager exportedFileManager() {
            return Mockito.mock(ExportedFileManager.class);
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() {
        Map<String, Object> props = new HashMap<>();
        props.put("db.svc1.url", "jdbc:hsqldb:mem:interceptor" + System.nanoTime());
        props.put("db.svc1.driverClass", "org.hsqldb.jdbc.JDBCDriver");
        props.put("db.svc1.username", "SA");
        props.put("db.svc1.password", "");
        props.put("db.svc1.maxPoolSize", "3");
        props.put("db.svc1.minPoolSize", "1");
        props.put("db.svc1.maxPageSize", "100");
        props.put("db.svc1.transactionTimeout", "30");
        props.put("db.svc1.dataSourceType", "WM_MANAGED_DATASOURCE");
        props.put("db.svc1.jndiName", "");
        props.put("db.svc1.dialect", "org.hibernate.dialect.HSQLDialect");
        props.put("db.svc1.hbm2ddl", "create");
        props.put("db.svc1.packages", "com.wavemaker.runtime.data.regsvc");
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", props));
        context.register(Infra.class, DataServiceAutoConfiguration.class, DataServiceBeansRegistrar.class);
        context.refresh();
        ServerVariables.use(name -> USER.get());
        tx = new TransactionTemplate(context.getBean("svc1TransactionManager", PlatformTransactionManager.class));
        dao = context.getBean("svc1.AuditedWidgetDao", WMGenericDao.class);
    }

    @AfterEach
    public void tearDown() {
        ServerVariables.reset();
        context.close();
    }

    @Test
    public void injectsTheServerVariableOnInsertAndUpdate() {
        USER.set("creator");
        AuditedWidget widget = new AuditedWidget();
        widget.setName("w");
        AuditedWidget created = tx.execute(status -> dao.create(widget));
        assertEquals("creator", dao.findById(created.getId()).getModifiedBy());

        USER.set("editor");
        created.setName("w2");
        tx.executeWithoutResult(status -> dao.update(created));
        assertEquals("editor", dao.findById(created.getId()).getModifiedBy());
    }
}
