package com.wavemaker.runtime.data;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.wavemaker.runtime.commons.file.manager.ExportedFileManager;
import com.wavemaker.runtime.commons.file.model.ExportType;
import com.wavemaker.runtime.data.dao.WMGenericDao;
import com.wavemaker.runtime.data.dao.procedure.WMProcedureExecutor;
import com.wavemaker.runtime.data.dao.query.WMQueryExecutor;
import com.wavemaker.runtime.data.export.DataExportOptions;
import com.wavemaker.runtime.data.export.ExportOptions;
import com.wavemaker.runtime.data.model.JavaType;
import com.wavemaker.runtime.data.model.QueryProcedureInput;
import com.wavemaker.runtime.data.model.queries.QueryParameter;
import com.wavemaker.runtime.data.model.queries.RuntimeQuery;
import com.wavemaker.runtime.data.regsvc.Widget;
import com.wavemaker.runtime.data.regsvc.WidgetView;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the named queries of svc1-queries.hbm.xml and the runtime queries, as the generated query services do.
 */
public class NamedQueryExecutionTest {

    private AnnotationConfigApplicationContext context;
    private WMQueryExecutor queryExecutor;
    private TransactionTemplate tx;

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
        props.put("db.svc1.url", "jdbc:hsqldb:mem:queries" + System.nanoTime());
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
        queryExecutor = context.getBean("svc1WMQueryExecutor", WMQueryExecutor.class);
        tx = new TransactionTemplate(context.getBean("svc1TransactionManager", PlatformTransactionManager.class));
        WMGenericDao<Widget, Integer> dao = context.getBean("svc1.WidgetDao", WMGenericDao.class);
        for (String name : new String[]{"a", "a", "b"}) {
            Widget widget = new Widget();
            widget.setName(name);
            tx.execute(status -> dao.create(widget));
        }
    }

    @AfterEach
    public void tearDown() {
        context.close();
    }

    @Test
    public void namedHqlQueryMapsAliasesToTheResponseType() {
        Page<WidgetView> page = tx.execute(status -> queryExecutor.executeNamedQuery("widgetsByName", Map.of("name", "a"),
            WidgetView.class, PageRequest.of(0, 10)));
        assertEquals(2, page.getTotalElements());
        assertEquals("a", page.getContent().get(0).getName());
        assertTrue(page.getContent().get(0).getId() > 0);
    }

    @Test
    public void namedHqlQuerySortsAndPagesByAMappedField() {
        Page<WidgetView> page = tx.execute(status -> queryExecutor.executeNamedQuery("widgetsByName", Map.of("name", "a"),
            WidgetView.class, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id"))));
        assertEquals(2, page.getTotalElements());
        assertEquals(1, page.getContent().size());
    }

    @Test
    public void namedNativeQueryMapsColumnsToTheResponseType() {
        Page<WidgetView> page = tx.execute(status -> queryExecutor.executeNamedQuery("nativeWidgets", Map.of("name", "b"),
            WidgetView.class, PageRequest.of(0, 10)));
        assertEquals(1, page.getContent().size());
        assertEquals("b", page.getContent().get(0).getName());
        assertTrue(page.getContent().get(0).getId() > 0);
    }

    @Test
    public void namedUpdateQueryReturnsTheUpdatedRowCount() {
        int updated = tx.execute(status -> queryExecutor.executeNamedQueryForUpdate("renameWidgets", Map.of("name", "z")));
        assertEquals(3, updated);
    }

    @Test
    public void runtimeHqlQueryWithAliasesAndATypedParameter() {
        RuntimeQuery query = new RuntimeQuery();
        query.setQueryString("select w.id as id, w.name as name from Widget w where w.name = :name");
        query.setNativeSql(false);
        query.setParameters(List.of(new QueryParameter("name", JavaType.STRING, false, "a")));
        Page<Object> page = tx.execute(status -> queryExecutor.executeRuntimeQuery(query, PageRequest.of(0, 10)));
        assertEquals(2, page.getContent().size());
        assertEquals(2, page.getTotalElements());
    }

    @Test
    public void runtimeNativeQuery() {
        RuntimeQuery query = new RuntimeQuery();
        query.setQueryString("select id, name from Widget where name = :name");
        query.setNativeSql(true);
        query.setParameters(List.of(new QueryParameter("name", JavaType.STRING, false, "b")));
        Page<Object> page = tx.execute(status -> queryExecutor.executeRuntimeQuery(query, PageRequest.of(0, 10)));
        assertEquals(1, page.getContent().size());
    }

    @Test
    public void exportsTheEntitiesAsCsv() {
        WMGenericDao<Widget, Integer> dao = context.getBean("svc1.WidgetDao", WMGenericDao.class);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        tx.execute(status -> {
            dao.export(new DataExportOptions(ExportType.CSV, 10, "name = 'a'"), PageRequest.of(0, 10), out);
            return null;
        });
        String csv = out.toString(StandardCharsets.UTF_8);
        assertEquals(2, csv.lines().filter(line -> line.contains("a")).count() - (csv.lines().findFirst().orElse("").contains("a") ? 1 : 0));
    }

    @Test
    public void exportsANamedHqlQueryAndANamedNativeQuery() {
        for (String queryName : new String[]{"widgetsByName", "nativeWidgets"}) {
            for (ExportType exportType : ExportType.values()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                tx.execute(status -> {
                    queryExecutor.exportNamedQueryData(new QueryProcedureInput<>(queryName, Map.of("name", "a"), WidgetView.class),
                        new ExportOptions(exportType, 10), PageRequest.of(0, 10), out);
                    return null;
                });
                assertTrue(out.size() > 0, queryName + " " + exportType);
                if (exportType == ExportType.CSV) {
                    assertTrue(out.toString(StandardCharsets.UTF_8).contains("a"), queryName);
                }
            }
        }
    }

    @Test
    public void executesANamedProcedureWithAnOutParameter() throws Exception {
        try (java.sql.Connection connection = context.getBean("svc1DataSource", javax.sql.DataSource.class).getConnection();
             java.sql.Statement statement = connection.createStatement()) {
            statement.execute("CREATE PROCEDURE ADD_ONE(IN a INT, OUT b INT) BEGIN ATOMIC SET b = a + 1; END");
        }
        WMProcedureExecutor procedureExecutor = context.getBean("svc1WMProcedureExecutor", WMProcedureExecutor.class);
        Map<?, ?> result = tx.execute(status -> procedureExecutor.executeNamedProcedure("addOne", Map.of("a", 4), Map.class));
        assertEquals(5, ((Number) result.get("b")).intValue());
    }
}
