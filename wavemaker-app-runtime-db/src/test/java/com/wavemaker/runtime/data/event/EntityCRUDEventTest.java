/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.event;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.ResolvableType;

import com.wavemaker.runtime.data.model.FetchQuery;
import com.wavemaker.runtime.data.support.entity.Department;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the "Entity Events" data-contract side of the five event-* scenarios in the spec: each event carries
 * exactly the right payload for its lifecycle point, and {@link EntityCRUDEvent#getResolvableType()} - which
 * Spring's generics-aware {@code @EventListener} matching depends on to route events by their entity type
 * parameter - resolves correctly.
 *
 * <p>The scenarios' other half - that pre fires strictly before post, and that a pre-handler throwing prevents
 * the operation and the post event - is dispatch behavior owned by {@code CRUDAspectManager} and the
 * package-private {@code *MethodInvocationHandler} classes in {@code com.wavemaker.runtime.data.aop} (that
 * package is where those classes actually live and are accessible from), and is covered there:
 * {@code CRUDAspectManagerTest.createPublishesPreCreateBeforeTheIdIsSetAndPostCreateWithThePersistedEntity()}
 * (event-pre-create-before-save / event-post-create-with-pk),
 * {@code CRUDAspectManagerTest.updatePublishesTheIncomingEntityBeforeAndTheReturnedEntityAfter()}
 * (event-pre-post-update), {@code CRUDAspectManagerTest.aPreDeleteListenerThatThrowsStopsTheDelete()}
 * (event-pre-post-delete-veto) and {@code CRUDAspectManagerTest.aPreFetchListListenerCanRewriteTheQueryTheServiceReceives()}
 * (event-post-fetch-list).
 */
class EntityCRUDEventTest {

    private static final String SERVICE_ID = "employeeService";

    @Test
    void preAndPostCreateEventsCarryTheEntityPassedToTheirConstructor() {
        Employee employee = new Employee();
        employee.setName("Alice");

        EntityPreCreateEvent<Employee> pre = new EntityPreCreateEvent<>(SERVICE_ID, Employee.class, employee);
        EntityPostCreateEvent<Employee> post = new EntityPostCreateEvent<>(SERVICE_ID, Employee.class, employee);

        assertEquals(employee, pre.getEntity());
        assertEquals(employee, post.getEntity());
        assertEquals(SERVICE_ID, pre.getServiceId());
        assertEquals(Employee.class, pre.getEntityClass());
    }

    @Test
    void preAndPostUpdateEventsCarryTheEntityPassedToTheirConstructor() {
        Employee employee = new Employee();
        employee.setName("Updated");

        EntityPreUpdateEvent<Employee> pre = new EntityPreUpdateEvent<>(SERVICE_ID, Employee.class, employee);
        EntityPostUpdateEvent<Employee> post = new EntityPostUpdateEvent<>(SERVICE_ID, Employee.class, employee);

        assertEquals(employee, pre.getEntity());
        assertEquals(employee, post.getEntity());
    }

    @Test
    void preDeleteEventCarriesTheRawEntityIdAndPostDeleteEventCarriesTheReturnedEntity() {
        Employee deleted = new Employee();
        deleted.setId(42L);

        EntityPreDeleteEvent<Employee> pre = new EntityPreDeleteEvent<>(SERVICE_ID, Employee.class, 42L);
        EntityPostDeleteEvent<Employee> post = new EntityPostDeleteEvent<>(SERVICE_ID, Employee.class, deleted);

        // Pre-delete only ever has the id available (the entity hasn't been loaded for the delete call) -
        // getEntity() on the pre-event is really "whatever identifier was passed in", not a hydrated entity.
        assertEquals(42L, pre.getEntity());
        assertEquals(deleted, post.getEntity());
    }

    @Test
    void preAndPostFetchEventsCarryTheIdAndAnOptionalOfTheFoundEntity() {
        Employee found = new Employee();
        found.setId(1L);

        EntityPreFetchEvent<Employee> pre = new EntityPreFetchEvent<>(SERVICE_ID, Employee.class, 1L);
        EntityPostFetchEvent<Employee> postFound = new EntityPostFetchEvent<>(SERVICE_ID, Employee.class, found);
        EntityPostFetchEvent<Employee> postMissing = new EntityPostFetchEvent<>(SERVICE_ID, Employee.class, null);

        assertEquals(1L, pre.getEntityId());
        assertTrue(postFound.getEntity().isPresent());
        assertEquals(found, postFound.getEntity().get());
        assertFalse(postMissing.getEntity().isPresent());
    }

    @Test
    void preFetchListEventCarriesTheFetchQueryAndPostFetchListEventCarriesThePageResult() {
        FetchQuery fetchQuery = new FetchQuery("active = true");
        org.springframework.data.domain.Page<Employee> page = new org.springframework.data.domain.PageImpl<>(List.of());

        EntityPreFetchListEvent<Employee> pre = new EntityPreFetchListEvent<>(SERVICE_ID, Employee.class, fetchQuery);
        EntityPostFetchListEvent<Employee> post = new EntityPostFetchListEvent<>(SERVICE_ID, Employee.class, page);

        assertEquals("active = true", pre.getFetchQuery().getQuery());
        assertEquals(page, post.getResults());
    }

    @Test
    void getResolvableTypeReflectsTheConcreteEventSubclassParameterizedWithTheEntityType() {
        EntityPostCreateEvent<Employee> event = new EntityPostCreateEvent<>(SERVICE_ID, Employee.class, new Employee());

        ResolvableType resolvableType = event.getResolvableType();

        assertEquals(EntityPostCreateEvent.class, resolvableType.getRawClass());
        assertEquals(Employee.class, resolvableType.getGeneric(0).getRawClass());
    }

    @Test
    void springDeliversAnEventOnlyToListenersForItsEntityType() {
        // Apps listen with @EventListener methods typed by entity, e.g. EntityPreCreateEvent<Employee>. Spring can
        // only route by that type argument through getResolvableType(), so this checks the routing for real.
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TypedListeners.class)) {
            TypedListeners listeners = context.getBean(TypedListeners.class);

            context.publishEvent(new EntityPreCreateEvent<>(SERVICE_ID, Employee.class, new Employee()));
            context.publishEvent(new EntityPreCreateEvent<>("departmentService", Department.class, new Department()));

            assertEquals(List.of("employee"), listeners.employeeEvents);
            assertEquals(List.of("department"), listeners.departmentEvents);
            assertEquals(2, listeners.allEvents.size());
        }
    }

    public static class TypedListeners {

        final List<String> employeeEvents = new ArrayList<>();

        final List<String> departmentEvents = new ArrayList<>();

        final List<EntityCRUDEvent<?>> allEvents = new ArrayList<>();

        @EventListener
        public void onEmployeeCreate(EntityPreCreateEvent<Employee> event) {
            employeeEvents.add("employee");
        }

        @EventListener
        public void onDepartmentCreate(EntityPreCreateEvent<Department> event) {
            departmentEvents.add("department");
        }

        @EventListener
        public void onAnyEntityEvent(EntityCRUDEvent<?> event) {
            allEvents.add(event);
        }
    }
}
