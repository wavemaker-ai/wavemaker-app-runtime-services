/*******************************************************************************
 * Copyright (C) 2022-2023 WaveMaker, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.wavemaker.runtime.data.dao;

import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.hibernate.OrderingMode;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;

import com.wavemaker.commons.MessageResource;
import com.wavemaker.commons.WMRuntimeException;
import com.wavemaker.runtime.commons.file.manager.ExportedFileManager;
import com.wavemaker.runtime.commons.file.model.DownloadResponse;
import com.wavemaker.runtime.commons.file.model.Downloadable;
import com.wavemaker.runtime.commons.file.model.ExportedFileContentWrapper;
import com.wavemaker.runtime.data.dao.generators.EntityQueryGenerator;
import com.wavemaker.runtime.data.dao.generators.SimpleEntitiyQueryGenerator;
import com.wavemaker.runtime.data.dao.query.providers.AppRuntimeParameterProvider;
import com.wavemaker.runtime.data.dao.query.providers.ParametersProvider;
import com.wavemaker.runtime.data.dao.query.providers.RuntimeQueryProvider;
import com.wavemaker.runtime.data.dao.query.types.wmql.WMQLTypeHelper;
import com.wavemaker.runtime.data.dao.util.PageUtils;
import com.wavemaker.runtime.data.dao.util.QueryHelper;
import com.wavemaker.runtime.data.dao.validators.SortValidator;
import com.wavemaker.runtime.data.exception.EntityNotFoundException;
import com.wavemaker.runtime.commons.file.model.ExportType;
import com.wavemaker.runtime.data.export.DataExportOptions;
import com.wavemaker.runtime.data.export.DataExporter;
import com.wavemaker.runtime.data.export.QueryExtractor;
import com.wavemaker.runtime.data.export.hqlquery.HqlQueryExtractor;
import com.wavemaker.runtime.data.expression.QueryFilter;
import com.wavemaker.runtime.data.filter.WMQueryInfo;
import com.wavemaker.runtime.data.hql.SelectQueryBuilder;
import com.wavemaker.runtime.data.model.AggregationInfo;
import com.wavemaker.runtime.data.util.CriteriaUtils;
import com.wavemaker.runtime.data.util.DaoUtils;
import com.wavemaker.runtime.data.util.HibernateUtils;
import com.wavemaker.runtime.data.util.HqlQueryHelper;

public class WMGenericDaoImpl<E extends Serializable, I extends Serializable> implements
    WMGenericDao<E, I> {

    protected final Class<E> entityClass;
    protected final WMSessionTemplate template;
    protected final WMQLTypeHelper wmqlTypeHelper;
    protected EntityQueryGenerator<E, I> queryGenerator;

    protected SortValidator sortValidator;

    @Autowired
    private ExportedFileManager exportedFileManager;

    public WMGenericDaoImpl(Class<E> entityClass, WMSessionTemplate template, WMQLTypeHelper wmqlTypeHelper) {
        this.entityClass = entityClass;
        this.template = template;
        this.wmqlTypeHelper = wmqlTypeHelper;
    }

    public WMSessionTemplate getTemplate() {
        return this.template;
    }

    public WMQLTypeHelper getWMQLTypeHelper() {
        return this.wmqlTypeHelper;
    }

    @PostConstruct
    public void init() {
        if (getTemplate() == null) {
            throw new WMRuntimeException(MessageResource.create("com.wavemaker.runtime.hibernateTemplate.not.set"));
        }

        this.sortValidator = new SortValidator();
        queryGenerator = new SimpleEntitiyQueryGenerator<>(entityClass);
    }

    @Override
    @SuppressWarnings("unchecked")
    public E create(E entity) {
        getTemplate().save(entity);
        getTemplate().flush();
        return entity;
    }

    @Override
    public void update(E entity) {
        getTemplate().update(entity);
        getTemplate().flush();
    }

    @Override
    public void delete(E entity) {
        getTemplate().delete(entity);
    }

    @Override
    public E findById(I entityId) {
        final SelectQueryBuilder builder = queryGenerator.findById(entityId);

        return HqlQueryHelper.execute(getTemplate(), entityClass, builder, getWMQLTypeHelper())
            .orElseThrow(() -> new EntityNotFoundException(MessageResource.create("com.wavemaker.runtime.no.entity.exists.for.given.id"), entityId));
    }

    @Override
    public List<E> findByMultipleIds(final List<I> ids, final boolean orderedReturn) {
        final List<I> nonNullIds = ids.stream().filter(Objects::nonNull).collect(Collectors.toList());

        final List<E> result = getTemplate().execute(session -> session.findMultiple(entityClass, nonNullIds,
            orderedReturn ? OrderingMode.ORDERED : OrderingMode.UNORDERED));

        List<E> resultWithNulls = new ArrayList<>(ids.size());
        int index = 0;
        for (I id : ids) {
            if (id == null) {
                resultWithNulls.add(null);
            } else {
                resultWithNulls.add(result.get(index++));
            }
        }
        return resultWithNulls;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E findByUniqueKey(final Map<String, Object> fieldValueMap) {
        final SelectQueryBuilder builder = queryGenerator.findBy(fieldValueMap);

        return HqlQueryHelper.execute(getTemplate(), entityClass, builder, getWMQLTypeHelper())
            .orElseThrow(() -> new EntityNotFoundException(
                MessageResource.create("com.wavemaker.runtime.entity.not.found.for.given.map"), fieldValueMap));
    }

    @Override
    public Page<E> list(Pageable pageable) {
        return search(null, PageUtils.defaultIfNull(pageable));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Page getAssociatedObjects(
        final Object value, final String fieldName, final String key, final Pageable pageable) {
        Pageable validPageable = PageUtils.defaultIfNull(pageable);
        this.sortValidator.validate(validPageable, entityClass);
        return getTemplate().execute(session ->
            CriteriaUtils.searchByAssociation(session, entityClass, fieldName, key, value, validPageable));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Page<E> search(final QueryFilter[] queryFilters, final Pageable pageable) {
        Pageable validPageable = PageUtils.defaultIfNull(pageable);
        this.sortValidator.validate(validPageable, entityClass);
        DaoUtils.validateQueryFilters(queryFilters);
        return getTemplate().execute(session -> CriteriaUtils.search(session, entityClass, queryFilters, validPageable));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Page<E> searchByQuery(final String query, final Pageable pageable) {
        Pageable validPageable = PageUtils.defaultIfNull(pageable);

        this.sortValidator.validate(validPageable, entityClass);

        final SelectQueryBuilder builder = queryGenerator.searchByQuery(query);
        return HqlQueryHelper.execute(getTemplate(), entityClass, builder, validPageable, getWMQLTypeHelper());
    }

    @Override
    public long count() {
        return count("");
    }

    @Override
    public long count(final String query) {
        return getTemplate().execute(session -> {
            final WMQueryInfo queryInfo = queryGenerator.searchByQuery(query).build();
            return QueryHelper.getQueryResultCount(queryInfo, false, session, getWMQLTypeHelper());
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public Page<Map<String, Object>> getAggregatedValues(
        final AggregationInfo aggregationInfo, final Pageable pageable) {
        Pageable validPageable = PageUtils.defaultIfNull(pageable);

        this.sortValidator.validate(validPageable, entityClass);

        final SelectQueryBuilder builder = queryGenerator.getAggregatedValues(aggregationInfo);
        final Page result = HqlQueryHelper.execute(getTemplate(), Map.class, builder, validPageable, getWMQLTypeHelper());

        return (Page<Map<String, Object>>) result;
    }

    @Override
    public Downloadable export(final ExportType exportType, final String query, final Pageable pageable) {
        String fileId = exportedFileManager.registerFile(entityClass.getSimpleName() + exportType.getExtension(), outputStream ->
            export(new DataExportOptions(exportType, pageable.getPageSize(), query), pageable, outputStream));
        ExportedFileContentWrapper fileContents = exportedFileManager.getFileContent(fileId);
        return new DownloadResponse(fileContents.getInputStream(), MediaType.APPLICATION_OCTET_STREAM_VALUE, fileContents.getFileName());
    }

    @Override
    public void export(DataExportOptions options, Pageable pageable, OutputStream outputStream) {
        final Pageable validPageable = PageUtils.overrideExportSize(pageable, options.getExportSize());
        this.sortValidator.validate(validPageable, entityClass);

        getTemplate().execute(session -> {
            final WMQueryInfo queryInfo = queryGenerator.searchByQuery(options.getQuery()).build();
            final RuntimeQueryProvider<E> queryProvider = RuntimeQueryProvider.from(queryInfo, entityClass);
            ParametersProvider provider = new AppRuntimeParameterProvider(queryInfo, HibernateUtils.getTypeConfiguration(session), getWMQLTypeHelper());

            final Query<E> hqlQuery = queryProvider.getQuery(session, validPageable, provider);
            QueryExtractor queryExtractor = new HqlQueryExtractor(hqlQuery.scroll());
            DataExporter.export(queryExtractor, options, entityClass, outputStream);
            return null;
        });
    }

    @Override
    public E refresh(final E entity) {
        getTemplate().refresh(entity);
        return entity;
    }

    @Override
    public void evict(final E entity) {
        getTemplate().evict(entity);
    }

    @Override
    public <T> List<T> findAllChildren(final Class<T> childClass, final String parentFieldName, final Object parent) {
        return getTemplate().execute(session ->
            DaoUtils.findAllChildren(session, childClass, new DaoUtils.ChildrenFilter<>(parentFieldName, parent)));
    }

    @Override
    public <T> List<T> findRemainingChildren(final Class<T> childClass, final String parentFieldName, final Object parent,
                                             final List<T> existingChildren) {
        return getTemplate().execute(session ->
            DaoUtils.findAllRemainingChildren(session, childClass,
                new DaoUtils.ChildrenFilter<>(parentFieldName, parent, existingChildren)));
    }
}
