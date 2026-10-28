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
package com.wavemaker.runtime.data.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Bindable;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.SingularAttribute;

import org.hibernate.Session;
import org.hibernate.query.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.wavemaker.runtime.data.expression.QueryFilter;
import com.wavemaker.runtime.data.spring.WMPageImpl;

/**
 * @author <a href="mailto:anusha.dharmasagar@wavemaker.com">Anusha Dharmasagar</a>
 * @since 25/5/16
 */
public abstract class CriteriaUtils {

    public static final String SEARCH_PROPERTY_DELIMITER = ".";

    /**
     * Searches the entities which satisfy all the filters. A filter on a property of a related entity, like
     * {@code department.name}, joins the related entity.
     */
    public static <E> Page<E> search(Session session, Class<E> entityClass, QueryFilter[] queryFilters, Pageable pageable) {
        return query(session, entityClass, (builder, root, joins) -> predicates(builder, root, joins, queryFilters), null, pageable);
    }

    /**
     * Searches the entities which have an associated entity, reached by the field, with the given value for the key.
     */
    public static <E> Page<E> searchByAssociation(
        Session session, Class<E> entityClass, String fieldName, String key, Object value, Pageable pageable) {
        // The sort properties are the ones of the associated entity, as they were for the sub-criteria of Hibernate 5.
        return query(session, entityClass, (builder, root, joins) -> {
            From<?, ?> associated = join(root, fieldName, joins, fieldName);
            return new Predicate[]{builder.equal(associated.get(key), value)};
        }, fieldName, pageable);
    }

    private static <E> Page<E> query(
        Session session, Class<E> entityClass, PredicateBuilder predicateBuilder, String sortJoinName, Pageable pageable) {
        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaQuery<E> query = builder.createQuery(entityClass);
        Root<E> root = query.from(entityClass);
        Map<String, From<?, ?>> joins = new HashMap<>();
        query.select(root).where(predicateBuilder.build(builder, root, joins));
        if (pageable == null) {
            return new WMPageImpl<>(session.createQuery(query).list());
        }

        long count = count(session, entityClass, predicateBuilder);
        List<Order> orders = new ArrayList<>();
        From<?, ?> sortBase = sortJoinName == null ? root : joins.get(sortJoinName);
        // the joins of the sort are relative to the entity it sorts
        Map<String, From<?, ?>> sortJoins = sortJoinName == null ? joins : new HashMap<>();
        for (Sort.Order order : pageable.getSort()) {
            Path<?> path = getPath(sortBase, order.getProperty(), sortJoins);
            orders.add(order.getDirection() == Sort.Direction.DESC ? builder.desc(path) : builder.asc(path));
        }
        query.orderBy(orders);

        Query<E> hibernateQuery = session.createQuery(query);
        hibernateQuery.setFirstResult((int) pageable.getOffset());
        hibernateQuery.setMaxResults(pageable.getPageSize());
        return new WMPageImpl<>(hibernateQuery.list(), pageable, count);
    }

    private static <E> long count(Session session, Class<E> entityClass, PredicateBuilder predicateBuilder) {
        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<E> root = countQuery.from(entityClass);
        countQuery.select(builder.count(root)).where(predicateBuilder.build(builder, root, new HashMap<>()));
        Long count = session.createQuery(countQuery).uniqueResult();
        return count == null ? 0L : count;
    }

    private static Predicate[] predicates(
        CriteriaBuilder builder, Root<?> root, Map<String, From<?, ?>> joins, QueryFilter[] queryFilters) {
        List<Predicate> predicates = new ArrayList<>();
        if (queryFilters != null) {
            for (QueryFilter queryFilter : queryFilters) {
                Path<?> path = getPath(root, queryFilter.getAttributeName(), joins);
                predicates.add(queryFilter.getFilterCondition().predicate(builder, path, queryFilter.getAttributeValue()));
            }
        }
        return predicates.toArray(new Predicate[0]);
    }

    /**
     * Returns the path of the attribute, a name like {@code department.name} navigates to a property of a related entity. An
     * association on the way is joined once, an embedded property like {@code id.employeeId} is navigated without a join.
     */
    public static Path<?> getPath(From<?, ?> base, String attributeName, Map<String, From<?, ?>> joins) {
        String[] names = attributeName.split("\\" + SEARCH_PROPERTY_DELIMITER);
        Path<?> current = base;
        StringBuilder joinName = new StringBuilder();
        for (int i = 0; i < names.length - 1; i++) {
            joinName.append(i == 0 ? "" : SEARCH_PROPERTY_DELIMITER).append(names[i]);
            if (isAssociation(current, names[i])) {
                current = join((From<?, ?>) current, names[i], joins, joinName.toString());
            } else {
                current = current.get(names[i]);
            }
        }
        return current.get(names[names.length - 1]);
    }

    private static From<?, ?> join(From<?, ?> from, String name, Map<String, From<?, ?>> joins, String joinName) {
        return joins.computeIfAbsent(joinName, key -> from.join(name));
    }

    private static boolean isAssociation(Path<?> path, String name) {
        Bindable<?> model = path.getModel();
        ManagedType<?> managedType = null;
        if (model instanceof ManagedType<?> type) {
            managedType = type;
        } else if (model instanceof SingularAttribute<?, ?> attribute && attribute.getType() instanceof ManagedType<?> type) {
            managedType = type;
        }
        if (managedType == null) {
            return false;
        }
        Attribute<?, ?> attribute = managedType.getAttribute(name);
        return attribute.isAssociation() || attribute.isCollection();
    }

    @FunctionalInterface
    private interface PredicateBuilder {

        Predicate[] build(CriteriaBuilder builder, Root<?> root, Map<String, From<?, ?>> joins);
    }
}
