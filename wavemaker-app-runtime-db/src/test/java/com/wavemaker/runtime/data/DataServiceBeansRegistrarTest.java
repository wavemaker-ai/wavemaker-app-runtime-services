package com.wavemaker.runtime.data;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.orm.hibernate5.HibernateTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.wavemaker.runtime.commons.file.manager.ExportedFileManager;
import com.wavemaker.runtime.data.dao.WMGenericDao;
import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLDefaultTypeHelper;
import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLSqlServerTypeHelper;
import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLTypeHelper;
import com.wavemaker.runtime.data.regsvc.Widget;
import com.wavemaker.runtime.data.regsvc.service.WidgetService;
import com.wavemaker.runtime.data.util.NegatingTypeFilter;

import org.springframework.core.type.filter.AssignableTypeFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataServiceBeansRegistrarTest {

    private AnnotationConfigApplicationContext context;

    @Configuration
    @EnableTransactionManagement
    static class Infra {
        @Bean
        static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        static DataServiceBeansRegistrar registrar() {
            return new DataServiceBeansRegistrar();
        }

        @Bean
        ExportedFileManager exportedFileManager() {
            return Mockito.mock(ExportedFileManager.class);
        }

        @Bean
        AssignableTypeFilter historyEntitiesFilter() {
            return new AssignableTypeFilter(com.wavemaker.runtime.data.model.TemporalHistoryEntity.class);
        }

        @Bean
        NegatingTypeFilter nonHistoryEntitiesFilter() {
            return new NegatingTypeFilter(historyEntitiesFilter());
        }
    }

    @AfterEach
    public void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    public void registersTheBeansOfADataService() {
        start(Map.of());
        for (String name : new String[]{"svc1HikariConfig", "svc1WMManagedDataSource", "svc1DataSource", "svc1HibernateProperties",
            "svc1EntityInterceptor", "svc1SessionFactory", "svc1TransactionManager", "svc1Template", "svc1WMQLTypeHelper",
            "svc1WMQueryExecutor", "svc1WMProcedureExecutor", "svc1NativeQueryExecutor", "svc1HqlQueryExecutor",
            "svc1PageableHandlerMethodResolver", "svc1.WidgetDao", "svc1.WidgetService"}) {
            assertTrue(context.containsBean(name), "missing bean " + name);
        }
        assertFalse(context.containsBean("svc1HistorySessionFactory"), "no temporal entities, so no history beans");
        assertTrue(context.getBean("svc1WMQLTypeHelper") instanceof WMQLDefaultTypeHelper);
    }

    @Test
    public void generatedServiceWorksEndToEnd() {
        start(Map.of());
        WidgetService service = context.getBean(WidgetService.class);
        Widget widget = new Widget();
        widget.setName("w1");
        Widget created = service.create(widget);
        assertNotNull(created.getId());
        assertEquals("w1", service.get(created.getId()).getName());
        assertEquals(1, context.getBean("svc1.WidgetDao", WMGenericDao.class).count());
        assertNotNull(context.getBean("svc1Template", HibernateTemplate.class));
    }

    @Test
    public void usesTheSqlServerTypeHelperForSqlServer() {
        start(Map.of("db.svc1.dbType", "SQLServer"));
        assertTrue(context.getBean("svc1WMQLTypeHelper", WMQLTypeHelper.class) instanceof WMQLSqlServerTypeHelper);
    }

    @Test
    public void leavesAServiceAlreadyDeclaredInXmlAlone() {
        Map<String, Object> props = new HashMap<>();
        props.put("db.svc1.url", "jdbc:hsqldb:mem:x");
        props.put("db.svc1.packages", "com.wavemaker.runtime.data.regsvc");
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", props));
        context.registerBean("svc1SessionFactory", Object.class, Object::new);
        context.register(Infra.class);
        context.refresh();
        assertFalse(context.containsBean("svc1Template"));
    }

    @Test
    public void keepsABeanTheProjectAlreadyDefines() {
        Map<String, Object> props = new HashMap<>();
        props.put("db.svc1.url", "jdbc:hsqldb:mem:override" + System.nanoTime());
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
        // as project-user-spring.xml would define them, before the registrar runs
        context.registerBean("svc1WMQLTypeHelper", WMQLTypeHelper.class, WMQLSqlServerTypeHelper::new);
        context.registerBean("svc1.WidgetService", WidgetService.class, WidgetService::new);
        context.register(Infra.class);
        context.refresh();
        assertTrue(context.getBean("svc1WMQLTypeHelper") instanceof WMQLSqlServerTypeHelper);
        assertTrue(context.containsBean("svc1Template"), "the other beans are still registered");
    }

    private void start(Map<String, Object> extra) {
        Map<String, Object> props = new HashMap<>();
        props.put("db.svc1.url", "jdbc:hsqldb:mem:registrar" + System.nanoTime());
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
        props.putAll(extra);
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", props));
        context.register(Infra.class);
        context.refresh();
    }
}
