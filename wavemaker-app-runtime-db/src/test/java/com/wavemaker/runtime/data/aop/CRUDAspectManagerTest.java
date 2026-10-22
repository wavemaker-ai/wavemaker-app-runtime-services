/*******************************************************************************
 * Copyright (C) 2025-2026 WaveMaker, Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of WaveMaker, Inc.
 * You shall not disclose such Confidential Information and shall use it only in accordance with the
 * terms of the source code license agreement you entered into with WaveMaker, Inc.
 ******************************************************************************/
package com.wavemaker.runtime.data.aop;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.wavemaker.runtime.data.annotations.EntityService;
import com.wavemaker.runtime.data.event.EntityCRUDEvent;
import com.wavemaker.runtime.data.event.EntityPostCreateEvent;
import com.wavemaker.runtime.data.event.EntityPostDeleteEvent;
import com.wavemaker.runtime.data.event.EntityPostFetchEvent;
import com.wavemaker.runtime.data.event.EntityPostFetchListEvent;
import com.wavemaker.runtime.data.event.EntityPostUpdateEvent;
import com.wavemaker.runtime.data.event.EntityPreCreateEvent;
import com.wavemaker.runtime.data.event.EntityPreDeleteEvent;
import com.wavemaker.runtime.data.event.EntityPreFetchEvent;
import com.wavemaker.runtime.data.event.EntityPreFetchListEvent;
import com.wavemaker.runtime.data.event.EntityPreUpdateEvent;
import com.wavemaker.runtime.data.expression.QueryFilter;
import com.wavemaker.runtime.data.support.TestReflectionUtils;
import com.wavemaker.runtime.data.support.entity.Employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "CRUD AOP" section: {@link CRUDAspectManager} wraps every public method of an {@code @EntityService} class
 * and, when a handler matches the method, the handler publishes a pre event before the call and a post event after
 * it. The aspect is woven for real with {@link AspectJProxyFactory} around {@link GeneratedEmployeeService}, which
 * has the same methods as a generated {@code <Entity>ServiceImpl}, the class real apps annotate.
 *
 * <p>The spec expects every matching handler to run in registration order; only the first matching one runs.
 */
class CRUDAspectManagerTest {

    private final RecordingApplicationEventPublisher publisher = new RecordingApplicationEventPublisher();

    @Test
    void createPublishesPreCreateBeforeTheIdIsSetAndPostCreateWithThePersistedEntity() {
        // Both events hold the same Employee instance, so the id is captured at the moment each is published.
        List<Long> idAtEachPublish = new ArrayList<>();
        GeneratedEmployeeService service = proxy(event -> {
            if (event instanceof EntityPreCreateEvent<?> pre) {
                idAtEachPublish.add(((Employee) pre.getEntity()).getId());
            } else if (event instanceof EntityPostCreateEvent<?> post) {
                idAtEachPublish.add(((Employee) post.getEntity()).getId());
            }
        }, new CreateMethodInvocationHandler());

        Employee created = service.create(employee("Alice"));

        assertEquals(Arrays.asList(null, created.getId()), idAtEachPublish);
    }

    @Test
    void updatePublishesTheIncomingEntityBeforeAndTheReturnedEntityAfter() {
        GeneratedEmployeeService service = proxy(publisher, new UpdateMethodInvocationHandler());
        Employee employee = employee("Original");
        employee.setId(7L);

        Employee updated = service.update(employee);

        assertEquals("Original", ((Employee) event(0, EntityPreUpdateEvent.class).getEntity()).getName());
        assertSame(updated, event(1, EntityPostUpdateEvent.class).getEntity());
    }

    @Test
    void getByIdAndFindByIdPublishTheIdBeforeAndTheFoundEntityAfter() {
        GeneratedEmployeeService service = proxy(publisher, new FindMethodInvocationHandler());
        Employee created = service.create(employee("Alice"));

        service.getById(created.getId());
        service.findById(created.getId());

        assertEquals(4, publisher.getPublishedEvents().size());
        for (int first : new int[]{0, 2}) {
            assertEquals(created.getId(), event(first, EntityPreFetchEvent.class).getEntityId());
            assertSame(created, event(first + 1, EntityPostFetchEvent.class).getEntity().orElseThrow());
        }
    }

    @Test
    void deleteByIdPublishesTheIdBeforeAndTheDeletedEntityAfter() {
        GeneratedEmployeeService service = proxy(publisher, new DeleteMethodInvocationHandler());
        Employee created = service.create(employee("Alice"));

        service.delete(created.getId());

        assertEquals(created.getId(), event(0, EntityPreDeleteEvent.class).getEntity());
        assertSame(created, event(1, EntityPostDeleteEvent.class).getEntity());
    }

    @Test
    void deleteByEntityPublishesNothing() {
        // DeleteMethodInvocationHandler only matches a delete whose single parameter is not the entity type.
        GeneratedEmployeeService service = proxy(publisher, new DeleteMethodInvocationHandler());
        Employee created = service.create(employee("Alice"));

        service.delete(created);

        assertTrue(publisher.getPublishedEvents().isEmpty());
    }

    @Test
    void aPreDeleteListenerThatThrowsStopsTheDelete() {
        GeneratedEmployeeService service = proxy(event -> {
            if (event instanceof EntityPreDeleteEvent<?>) {
                throw new IllegalStateException("delete vetoed");
            }
        }, new DeleteMethodInvocationHandler());
        Employee created = service.create(employee("Alice"));

        assertThrows(IllegalStateException.class, () -> service.delete(created.getId()));

        assertSame(created, service.findById(created.getId()));
    }

    @Test
    void aPreFetchListListenerCanRewriteTheQueryTheServiceReceives() {
        GeneratedEmployeeService service = proxy(event -> {
            publisher.publishEvent(event);
            if (event instanceof EntityPreFetchListEvent<?> pre) {
                pre.getFetchQuery().setQuery("active = true");
            }
        }, new FindAllMethodInvocationHandler());
        service.create(employee("Alice"));

        Page<Employee> page = service.findAll("name = 'Alice'", PageRequest.of(0, 10));

        assertEquals("active = true", service.getLastQuery());
        // The post event carries the page unchanged, pagination included.
        assertSame(page, event(1, EntityPostFetchListEvent.class).getResults());
    }

    @Test
    void aPostFetchListListenerCanMaskFieldsBeforeTheCallerSeesThePage() {
        // The post event carries the page the caller gets back, so a listener's changes reach the caller.
        GeneratedEmployeeService service = proxy(event -> {
            if (event instanceof EntityPostFetchListEvent<?> post) {
                post.getResults().forEach(row -> ((Employee) row).setSalary(null));
            }
        }, new FindAllMethodInvocationHandler());
        Employee alice = employee("Alice");
        alice.setSalary(new BigDecimal("50000"));
        service.create(alice);

        Page<Employee> page = service.findAll("", PageRequest.of(0, 10));

        assertNull(page.getContent().get(0).getSalary());
    }

    @Test
    void findAllWithQueryFiltersPublishesNothing() {
        // FindAllMethodInvocationHandler only matches a findAll whose first parameter is the query string.
        GeneratedEmployeeService service = proxy(publisher, new FindAllMethodInvocationHandler());

        service.findAll(new QueryFilter[0], PageRequest.of(0, 10));

        assertTrue(publisher.getPublishedEvents().isEmpty());
    }

    @Test
    void everyEventCarriesTheServiceIdAndEntityClassFromTheAnnotation() {
        GeneratedEmployeeService service = proxy(publisher, new CreateMethodInvocationHandler());

        service.create(employee("Alice"));

        for (Object event : publisher.getPublishedEvents()) {
            assertEquals("hrdb", ((EntityCRUDEvent<?>) event).getServiceId());
            assertEquals(Employee.class, ((EntityCRUDEvent<?>) event).getEntityClass());
        }
    }

    @Test
    void whenTwoHandlersMatchTheSameMethodOnlyTheFirstRegisteredOneRuns() {
        List<String> invocations = new ArrayList<>();
        GeneratedEmployeeService service = proxyWithHandlers(
            recordingCreateHandler(invocations, "first"), recordingCreateHandler(invocations, "second"));

        service.create(new Employee());

        assertEquals(List.of("first-pre", "first-post"), invocations);
    }

    @Test
    void withNoHandlersTheMethodRunsWithNoEvents() {
        GeneratedEmployeeService service = proxyWithHandlers();

        Employee created = service.create(new Employee());

        assertNotNull(created.getId());
        assertTrue(service.findAll("", PageRequest.of(0, 10)).getContent().contains(created));
    }

    @Test
    void methodsInheritedFromAnUnannotatedSuperclassAreNotIntercepted() {
        // @within matches the class that declares the method, not the runtime type. Generated services declare
        // their CRUD methods themselves; if one ever inherited them instead, its events would silently stop.
        CreateMethodInvocationHandler handler = handler(new CreateMethodInvocationHandler(), publisher);
        AspectJProxyFactory factory = new AspectJProxyFactory(new InheritingEmployeeService());
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect(handler));
        InheritingEmployeeService service = factory.getProxy();

        service.create(employee("Alice"));

        assertTrue(publisher.getPublishedEvents().isEmpty());
    }

    private GeneratedEmployeeService proxy(ApplicationEventPublisher eventPublisher, CRUDMethodInvocationHandler handler) {
        return proxyWithHandlers(handler(handler, eventPublisher));
    }

    private static GeneratedEmployeeService proxyWithHandlers(CRUDMethodInvocationHandler... handlers) {
        AspectJProxyFactory factory = new AspectJProxyFactory(new GeneratedEmployeeService());
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect(handlers));
        return factory.getProxy();
    }

    private static CRUDAspectManager aspect(CRUDMethodInvocationHandler... handlers) {
        CRUDAspectManager aspectManager = new CRUDAspectManager();
        TestReflectionUtils.setField(aspectManager, "crudMethodInvocationHandlerList", List.of(handlers));
        return aspectManager;
    }

    private static <H extends CRUDMethodInvocationHandler> H handler(H handler, ApplicationEventPublisher eventPublisher) {
        TestReflectionUtils.setField(handler, "applicationEventPublisher", eventPublisher);
        return handler;
    }

    private <T> T event(int index, Class<T> type) {
        return assertInstanceOf(type, publisher.getPublishedEvents().get(index));
    }

    private static Employee employee(String name) {
        Employee employee = new Employee();
        employee.setName(name);
        return employee;
    }

    private static CRUDMethodInvocationHandler recordingCreateHandler(List<String> invocations, String label) {
        return new CRUDMethodInvocationHandler() {
            @Override
            public void preHandle(String serviceId, Class entityClass, Method method, Object[] args) {
                invocations.add(label + "-pre");
            }

            @Override
            public void postHandle(String serviceId, Class entityClass, Method method, Object retVal) {
                invocations.add(label + "-post");
            }

            @Override
            public boolean matches(Class entityClass, Method method) {
                return "create".equals(method.getName());
            }
        };
    }

    public static class UnannotatedEmployeeService {

        public Employee create(Employee employee) {
            employee.setId(1L);
            return employee;
        }
    }

    @EntityService(serviceId = "hrdb", entityClass = Employee.class)
    public static class InheritingEmployeeService extends UnannotatedEmployeeService {
    }
}
