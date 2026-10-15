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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.hibernate5.HibernateTemplate;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.orm.hibernate5.LocalSessionFactoryBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.wavemaker.runtime.commons.file.manager.ExportedFileManager;
import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLDefaultTypeHelper;
import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLTypeHelper;
import com.wavemaker.runtime.data.dao.registrar.TestChild;
import com.wavemaker.runtime.data.dao.registrar.TestParent;

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

        LocalSessionFactoryBean sessionFactory = new LocalSessionFactoryBean();
        sessionFactory.setDataSource((DataSource) dataSource);
        sessionFactory.setAnnotatedClasses(TestParent.class, TestChild.class);
        Properties props = new Properties();
        props.setProperty("hibernate.dialect", "org.hibernate.dialect.HSQLDialect");
        props.setProperty("hibernate.hbm2ddl.auto", "create");
        sessionFactory.setHibernateProperties(props);
        sessionFactory.afterPropertiesSet();

        tx = new TransactionTemplate(new HibernateTransactionManager(sessionFactory.getObject()));
        context = new AnnotationConfigApplicationContext();
        context.registerBean(SERVICE_ID + "Template", HibernateTemplate.class,
            () -> new HibernateTemplate(sessionFactory.getObject()));
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
