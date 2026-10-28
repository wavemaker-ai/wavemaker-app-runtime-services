package com.wavemaker.runtime.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HibernatePropertiesFactoryBeanTest {

    @Test
    public void versionedDialectsOfHibernate5AreMappedToTheDialectOfTheDatabase() {
        assertEquals("org.hibernate.dialect.MySQLDialect", HibernatePropertiesFactoryBean.unversioned("org.hibernate.dialect.MySQL5Dialect"));
        assertEquals("org.hibernate.dialect.MySQLDialect", HibernatePropertiesFactoryBean.unversioned("org.hibernate.dialect.MySQL57Dialect"));
        assertEquals("org.hibernate.dialect.MySQLDialect", HibernatePropertiesFactoryBean.unversioned("org.hibernate.dialect.MySQL5InnoDBDialect"));
        assertEquals("org.hibernate.dialect.MySQLDialect", HibernatePropertiesFactoryBean.unversioned("org.hibernate.dialect.MySQL8Dialect"));
        assertEquals("org.hibernate.dialect.PostgreSQLDialect", HibernatePropertiesFactoryBean.unversioned("org.hibernate.dialect.PostgreSQL82Dialect"));
        assertEquals("org.hibernate.dialect.PostgreSQLDialect", HibernatePropertiesFactoryBean.unversioned("org.hibernate.dialect.PostgreSQL95Dialect"));
        assertEquals("org.hibernate.dialect.SQLServerDialect", HibernatePropertiesFactoryBean.unversioned("org.hibernate.dialect.SQLServer2012Dialect"));
        assertEquals("org.hibernate.dialect.SQLServerDialect", HibernatePropertiesFactoryBean.unversioned("org.hibernate.dialect.SQLServer2008Dialect"));
        assertEquals("org.hibernate.dialect.OracleDialect", HibernatePropertiesFactoryBean.unversioned("org.hibernate.dialect.Oracle10gDialect"));
        assertEquals("org.hibernate.dialect.OracleDialect", HibernatePropertiesFactoryBean.unversioned("org.hibernate.dialect.Oracle12cDialect"));
        assertEquals("org.hibernate.dialect.OracleDialect", HibernatePropertiesFactoryBean.unversioned("org.hibernate.dialect.Oracle9iDialect"));
    }

    @Test
    public void currentAndWaveMakerDialectsAreKept() {
        assertEquals("org.hibernate.dialect.PostgreSQLDialect", HibernatePropertiesFactoryBean.unversioned("org.hibernate.dialect.PostgreSQLDialect"));
        assertEquals("org.hibernate.dialect.HSQLDialect", HibernatePropertiesFactoryBean.unversioned("org.hibernate.dialect.HSQLDialect"));
        assertEquals("com.wavemaker.runtime.data.dialect.WMSQLServerDialect",
            HibernatePropertiesFactoryBean.unversioned("com.wavemaker.runtime.data.dialect.WMSQLServerDialect"));
        assertEquals("com.wavemaker.runtime.data.dialect.MySQLDialect",
            HibernatePropertiesFactoryBean.unversioned("com.wavemaker.runtime.data.dialect.MySQLDialect"));
    }
}
