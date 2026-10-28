package com.wavemaker.runtime.data.dao;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

import com.wavemaker.runtime.commons.file.manager.ExportedFileManager;
import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLDefaultTypeHelper;
import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLTypeHelper;
import com.wavemaker.runtime.data.dao.registrar.TestChild;
import com.wavemaker.runtime.data.dao.registrar.TestParent;
import com.wavemaker.runtime.data.expression.AttributeType;
import com.wavemaker.runtime.data.expression.QueryFilter;
import com.wavemaker.runtime.data.expression.Type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DaoBeanDefinitionsTest {

    private static final String SERVICE_ID = "svc";

    private AnnotationConfigApplicationContext context;
    private TransactionTemplate tx;

    @BeforeEach
    public void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:hsqldb:mem:daoregistrar" + System.nanoTime(), "SA", "");

        LocalContainerEntityManagerFactoryBean sessionFactory = new LocalContainerEntityManagerFactoryBean();
        sessionFactory.setDataSource((DataSource) dataSource);
        sessionFactory.setPackagesToScan("com.wavemaker.runtime.data.dao.registrar");
        sessionFactory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Properties props = new Properties();
        props.setProperty("hibernate.dialect", "org.hibernate.dialect.HSQLDialect");
        props.setProperty("hibernate.hbm2ddl.auto", "create");
        sessionFactory.setJpaProperties(props);
        sessionFactory.afterPropertiesSet();

        tx = new TransactionTemplate(new JpaTransactionManager(sessionFactory.getObject()));
        context = new AnnotationConfigApplicationContext();
        context.registerBean(SERVICE_ID + "Template", WMSessionTemplate.class,
            () -> new WMSessionTemplate(sessionFactory.getObject()));
        context.registerBean(SERVICE_ID + "WMQLTypeHelper", WMQLTypeHelper.class, WMQLDefaultTypeHelper::new);
        context.registerBean(ExportedFileManager.class, () -> Mockito.mock(ExportedFileManager.class));
        context.registerBean("daoRegistration", BeanDefinitionRegistryPostProcessor.class,
            () -> new BeanDefinitionRegistryPostProcessor() {
                @Override
                public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                    DaoBeanDefinitions.register(registry, "com.wavemaker.runtime.data.dao.registrar", SERVICE_ID);
                }
            });
        context.registerBean(org.springframework.context.annotation.CommonAnnotationBeanPostProcessor.class);
        context.registerBean(org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor.class);
        context.refresh();
    }

    @AfterEach
    public void tearDown() {
        context.close();
    }

    @Test
    public void registersOneDaoBeanPerInterface() {
        assertEquals(2, context.getBeansOfType(WMGenericDao.class).size());
        assertNotNull(context.getBean(SERVICE_ID + ".TestParentDao", WMGenericDao.class));
        assertNotNull(context.getBean(SERVICE_ID + ".TestChildDao", WMGenericDao.class));
    }

    @Test
    public void registeredDaoResolvesEntityClassAndCrud() {
        WMGenericDao<TestParent, Integer> dao = daoBean("TestParentDao");
        TestParent parent = new TestParent();
        parent.setName("p");
        TestParent saved = tx.execute(status -> dao.create(parent));
        assertNotNull(saved.getId());
        assertEquals("p", dao.findById(saved.getId()).getName());
        assertEquals(1, dao.count());
    }

    @Test
    public void findAllChildrenAndRemainingChildren() {
        WMGenericDao<TestParent, Integer> parentDao = daoBean("TestParentDao");
        WMGenericDao<TestChild, Integer> childDao = daoBean("TestChildDao");

        TestParent parent = tx.execute(status -> parentDao.create(parent("p1")));
        TestParent other = tx.execute(status -> parentDao.create(parent("p2")));
        TestChild c1 = tx.execute(status -> childDao.create(child("c1", parent)));
        TestChild c2 = tx.execute(status -> childDao.create(child("c2", parent)));
        tx.execute(status -> childDao.create(child("c3", other)));

        List<TestChild> all = childDao.findAllChildren(TestChild.class, "parent", parent);
        assertEquals(2, all.size());

        List<TestChild> remaining = childDao.findRemainingChildren(TestChild.class, "parent", parent, Arrays.asList(c1));
        assertEquals(1, remaining.size());
        assertEquals(c2.getId(), remaining.get(0).getId());

        assertTrue(childDao.findRemainingChildren(TestChild.class, "parent", parent, Arrays.asList(c1, c2)).isEmpty());
    }

    @Test
    public void searchFiltersOnOwnAndRelatedProperties() {
        WMGenericDao<TestParent, Integer> parentDao = daoBean("TestParentDao");
        WMGenericDao<TestChild, Integer> childDao = daoBean("TestChildDao");
        TestParent alpha = tx.execute(status -> parentDao.create(parent("alpha")));
        TestParent beta = tx.execute(status -> parentDao.create(parent("beta")));
        tx.execute(status -> childDao.create(child("c1", alpha)));
        tx.execute(status -> childDao.create(child("c2", alpha)));
        tx.execute(status -> childDao.create(child("c3", beta)));

        QueryFilter containing = new QueryFilter("name", "ALP", Type.CONTAINING, AttributeType.STRING);
        assertEquals(1, tx.execute(status -> parentDao.search(new QueryFilter[]{containing}, PageRequest.of(0, 10))).getTotalElements());

        QueryFilter byParentName = new QueryFilter("parent.name", "alpha", Type.EQUALS, AttributeType.STRING);
        assertEquals(2, tx.execute(status -> childDao.search(new QueryFilter[]{byParentName}, PageRequest.of(0, 10))).getTotalElements());

        QueryFilter inValues = new QueryFilter("name", Arrays.asList("c1", "c3"), Type.IN, AttributeType.STRING);
        QueryFilter notNull = new QueryFilter("name", null, Type.NULL, AttributeType.STRING);
        assertEquals(2, tx.execute(status -> childDao.search(new QueryFilter[]{inValues}, PageRequest.of(0, 10))).getTotalElements());
        assertEquals(0, tx.execute(status -> childDao.search(new QueryFilter[]{notNull}, PageRequest.of(0, 10))).getTotalElements());
    }

    @Test
    public void searchPagesAndSortsByARelatedProperty() {
        WMGenericDao<TestParent, Integer> parentDao = daoBean("TestParentDao");
        WMGenericDao<TestChild, Integer> childDao = daoBean("TestChildDao");
        TestParent alpha = tx.execute(status -> parentDao.create(parent("alpha")));
        TestParent beta = tx.execute(status -> parentDao.create(parent("beta")));
        tx.execute(status -> childDao.create(child("c1", beta)));
        tx.execute(status -> childDao.create(child("c2", alpha)));
        tx.execute(status -> childDao.create(child("c3", alpha)));

        Pageable firstPage = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "parent.name"));
        Page<TestChild> page = tx.execute(status -> childDao.search(null, firstPage));
        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertEquals("c1", page.getContent().get(0).getName());
        assertEquals(1, tx.execute(status -> childDao.search(null, PageRequest.of(1, 2))).getContent().size());
    }

    @Test
    public void associatedObjectsAreTheEntitiesOfTheAssociation() {
        WMGenericDao<TestParent, Integer> parentDao = daoBean("TestParentDao");
        WMGenericDao<TestChild, Integer> childDao = daoBean("TestChildDao");
        TestParent alpha = tx.execute(status -> parentDao.create(parent("alpha")));
        tx.execute(status -> childDao.create(child("c1", alpha)));
        Page<?> page = tx.execute(status -> childDao.getAssociatedObjects("alpha", "parent", "name", PageRequest.of(0, 10)));
        assertEquals(1, page.getTotalElements());
    }

    @SuppressWarnings("unchecked")
    private <E> WMGenericDao<E, Integer> daoBean(String simpleName) {
        return (WMGenericDao<E, Integer>) context.getBean(SERVICE_ID + "." + simpleName);
    }

    private TestParent parent(String name) {
        TestParent p = new TestParent();
        p.setName(name);
        return p;
    }

    private TestChild child(String name, TestParent parent) {
        TestChild c = new TestChild();
        c.setName(name);
        c.setParent(parent);
        return c;
    }
}
