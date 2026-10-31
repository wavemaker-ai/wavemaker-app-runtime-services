/*******************************************************************************
 * Copyright (C) 2024-2025 WaveMaker, Inc.
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
package com.wavemaker.runtime.exception.resolver;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.Path;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraintvalidation.SupportedValidationTarget;
import jakarta.validation.constraintvalidation.ValidationTarget;

import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;
import com.wavemaker.commons.InvalidInputException;
import com.wavemaker.commons.MessageResource;
import com.wavemaker.commons.UnAuthorizedResourceAccessException;
import com.wavemaker.commons.WMException;
import com.wavemaker.commons.WMRuntimeException;
import com.wavemaker.commons.core.web.rest.ErrorResponse;
import com.wavemaker.commons.core.web.rest.ErrorResponses;
import com.wavemaker.runtime.commons.WMAppContext;

public class ApplicationRestServiceExceptionResolverTest {

    private final ApplicationRestServiceExceptionResolver resolver = new ApplicationRestServiceExceptionResolver();

    private MockedStatic<WMAppContext> wmAppContextMock;
    private Validator validator;

    @BeforeEach
    public void init() {
        // error response parameters go through XSSSecurityHandler, which looks for the security config bean in the app context
        WMAppContext wmAppContext = Mockito.mock(WMAppContext.class);
        Mockito.when(wmAppContext.getSpringBean("WMAppSecurityConfig"))
            .thenThrow(new NoSuchBeanDefinitionException("WMAppSecurityConfig"));
        wmAppContextMock = Mockito.mockStatic(WMAppContext.class);
        wmAppContextMock.when(WMAppContext::getInstance).thenReturn(wmAppContext);

        validator = Validation.byProvider(HibernateValidator.class).configure()
            .messageInterpolator(new ParameterMessageInterpolator(Set.of(Locale.ENGLISH), Locale.ENGLISH, false))
            .parameterNameProvider(new ParameterNameProvider() {
                @Override
                public List<String> getParameterNames(Constructor<?> constructor) {
                    return names(constructor);
                }

                @Override
                public List<String> getParameterNames(Method method) {
                    return names(method);
                }

                private List<String> names(Executable executable) {
                    return Arrays.stream(executable.getParameters()).map(p -> "p_" + p.getName()).toList();
                }
            })
            .buildValidatorFactory().getValidator();
    }

    @AfterEach
    public void tearDown() {
        wmAppContextMock.close();
    }

    @Test
    public void testPropertyViolation() {
        List<ErrorResponse> errors = resolve(validator.validate(new User("", new Address("Hyderabad"), List.of("a"))));
        assertSingleError(errors, "name", "must not be blank");
    }

    @Test
    public void testNestedPropertyViolation() {
        List<ErrorResponse> errors = resolve(validator.validate(new User("john", new Address(""), List.of("a"))));
        assertSingleError(errors, "city", "must not be blank");
    }

    @Test
    public void testIterableElementViolation() {
        Set<ConstraintViolation<User>> violations = validator.validate(new User("john", new Address("Hyderabad"), List.of("a", " ")));
        Assertions.assertEquals("tags[1].<list element>", violations.iterator().next().getPropertyPath().toString());
        assertSingleError(resolve(violations), "<list element>", "must not be blank");
    }

    @Test
    public void testIterableViolation() {
        List<ErrorResponse> errors = resolve(validator.validate(new User("john", new Address("Hyderabad"), List.of())));
        assertSingleError(errors, "tags", "size must be between 1 and 2");
    }

    @Test
    public void testMethodParameterViolation() throws NoSuchMethodException {
        Service service = new Service();
        Method method = Service.class.getMethod("greet", String.class, int.class);
        Set<ConstraintViolation<Service>> violations = validator.forExecutables()
            .validateParameters(service, method, new Object[]{null, 1});
        Assertions.assertEquals("greet.p_userName", violations.iterator().next().getPropertyPath().toString());
        assertSingleError(resolve(violations), "p_userName", "must not be null");
    }

    @Test
    public void testMethodParameterNestedBeanViolation() throws NoSuchMethodException {
        Method method = Service.class.getMethod("save", User.class);
        Set<ConstraintViolation<Service>> violations = validator.forExecutables()
            .validateParameters(new Service(), method, new Object[]{new User("", new Address("Hyderabad"), List.of("a"))});
        Assertions.assertEquals("save.p_user.name", violations.iterator().next().getPropertyPath().toString());
        assertSingleError(resolve(violations), "name", "must not be blank");
    }

    @Test
    public void testConstructorParameterViolation() throws NoSuchMethodException {
        Constructor<Address> constructor = Address.class.getConstructor(String.class);
        Set<ConstraintViolation<Address>> violations = validator.forExecutables()
            .validateConstructorParameters(constructor, new Object[]{""});
        Assertions.assertEquals("Address.p_city", violations.iterator().next().getPropertyPath().toString());
        assertSingleError(resolve(violations), "p_city", "must not be blank");
    }

    @Test
    public void testReturnValueViolation() throws NoSuchMethodException {
        Method method = Service.class.getMethod("greet", String.class, int.class);
        Set<ConstraintViolation<Service>> violations = validator.forExecutables()
            .validateReturnValue(new Service(), method, "");
        assertSingleError(resolve(violations), "<return value>", "must not be blank");
    }

    @Test
    public void testCrossParameterViolation() throws NoSuchMethodException {
        Method method = Service.class.getMethod("range", int.class, int.class);
        Set<ConstraintViolation<Service>> violations = validator.forExecutables()
            .validateParameters(new Service(), method, new Object[]{5, 1});
        Assertions.assertEquals("range.<cross-parameter>", violations.iterator().next().getPropertyPath().toString());
        assertSingleError(resolve(violations), "<cross-parameter>", "from must not be greater than to");
    }

    @Test
    public void testMultipleViolations() {
        List<ErrorResponse> errors = resolve(validator.validate(new User(null, new Address(" "), List.of("a", ""))));
        Assertions.assertEquals(3, errors.size());
        Assertions.assertEquals(Set.of("name", "city", "<list element>"),
            Set.of(errors.get(0).getParameters().get(0), errors.get(1).getParameters().get(0),
                errors.get(2).getParameters().get(0)));
    }

    @Test
    public void testNoViolations() {
        Assertions.assertTrue(resolve(Set.of()).isEmpty());
    }

    @Test
    public void testClassLevelViolation() {
        List<ErrorResponse> errors = resolve(validator.validate(new Account("admin")));
        Assertions.assertEquals(1, errors.size());
        Assertions.assertEquals(Arrays.asList(null, "must not be admin"), errors.get(0).getParameters());
    }

    @Test
    public void testNullAndEmptyPropertyPath() {
        ConstraintViolation<?> nullPath = Mockito.mock(ConstraintViolation.class);
        Mockito.when(nullPath.getMessage()).thenReturn("no path");
        ConstraintViolation<?> emptyPath = Mockito.mock(ConstraintViolation.class);
        Mockito.when(emptyPath.getMessage()).thenReturn("empty path");
        Path path = Mockito.mock(Path.class);
        Mockito.when(path.iterator()).thenReturn(Collections.emptyIterator());
        Mockito.when(emptyPath.getPropertyPath()).thenReturn(path);
        Set<ConstraintViolation<?>> violations = Set.of(nullPath, emptyPath);
        List<ErrorResponse> errors = resolve(violations);
        Assertions.assertEquals(Set.of(List.of("", "no path"), List.of("", "empty path")),
            Set.of(errors.get(0).getParameters(), errors.get(1).getParameters()));
    }

    @Test
    public void testNullConstraintViolations() {
        Assertions.assertTrue(resolve(new ConstraintViolationException("invalid", null),
            HttpServletResponse.SC_BAD_REQUEST).isEmpty());
    }

    @Test
    public void testContributedResolverTakesPrecedence() throws ReflectiveOperationException {
        setErrorResponseResolvers(List.of(ex -> Optional.empty(),
            ex -> Optional.of(new ErrorResolution(HttpServletResponse.SC_CONFLICT, MessageResource.ENTITY_NOT_FOUND, "Employee", 1)),
            ex -> Optional.of(new ErrorResolution(HttpServletResponse.SC_NOT_FOUND, MessageResource.INVALID_INPUT))));
        List<ErrorResponse> errors = resolve(new IllegalStateException("ignored"), HttpServletResponse.SC_CONFLICT);
        assertSingleError(errors, MessageResource.ENTITY_NOT_FOUND, "Employee", "1");
    }

    @Test
    public void testUnrecognizedContributedResolverFallsBack() throws ReflectiveOperationException {
        setErrorResponseResolvers(List.of(ex -> Optional.empty()));
        assertSingleError(resolve(new IllegalStateException("oops"), HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
            MessageResource.UNEXPECTED_ERROR, "oops");
    }

    @Test
    public void testMethodArgumentTypeMismatch() {
        Exception ex = new MethodArgumentTypeMismatchException("abc", Integer.class, "age", null, null);
        assertSingleError(resolve(ex, HttpServletResponse.SC_BAD_REQUEST), MessageResource.INVALID_INPUT,
            "The input for age is invalid.");
    }

    @Test
    public void testMethodArgumentNotValid() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "user");
        bindingResult.addError(new FieldError("user", "name", "name is bad"));
        bindingResult.addError(new ObjectError("user", "user is bad"));
        List<ErrorResponse> errors = resolve(methodArgumentNotValidException(bindingResult),
            HttpServletResponse.SC_BAD_REQUEST);
        Assertions.assertEquals(2, errors.size());
        Assertions.assertEquals(MessageResource.INVALID_FIELD_VALUE.getMessageKey(), errors.get(0).getMessageKey());
        Assertions.assertEquals(List.of("name", "name is bad"), errors.get(0).getParameters());
        Assertions.assertEquals(MessageResource.INVALID_OBJECT.getMessageKey(), errors.get(1).getMessageKey());
        Assertions.assertEquals(List.of("user", "user is bad"), errors.get(1).getParameters());
    }

    @Test
    public void testMethodArgumentNotValidWithoutErrors() throws NoSuchMethodException {
        List<ErrorResponse> errors = resolve(
            methodArgumentNotValidException(new BeanPropertyBindingResult(new Object(), "user")),
            HttpServletResponse.SC_BAD_REQUEST);
        Assertions.assertTrue(errors.isEmpty());
    }

    @Test
    public void testHttpRequestMethodNotSupported() {
        Exception ex = new HttpRequestMethodNotSupportedException("PATCH");
        assertSingleError(resolve(ex, HttpServletResponse.SC_BAD_REQUEST), MessageResource.INVALID_INPUT,
            ex.getMessage());
    }

    @Test
    public void testHttpMessageNotReadableUnrecognizedProperty() {
        Exception cause = Assertions.assertThrows(UnrecognizedPropertyException.class,
            () -> JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()
                .readValue("{\"bogus\":1}", JsonPayload.class));
        assertSingleError(resolve(httpMessageNotReadableException(cause), HttpServletResponse.SC_BAD_REQUEST),
            MessageResource.UNRECOGNIZED_FIELD, "bogus");
    }

    @Test
    public void testHttpMessageNotReadableInvalidJson() {
        Exception cause = Assertions.assertThrows(DatabindException.class,
            () -> JsonMapper.builder().build().readValue("{\"name\":[1]}", JsonPayload.class));
        assertSingleError(resolve(httpMessageNotReadableException(cause), HttpServletResponse.SC_BAD_REQUEST),
            MessageResource.INVALID_JSON, cause.getMessage());
    }

    @Test
    public void testHttpMessageNotReadableOtherCauseOrNoCause() {
        assertSingleError(resolve(httpMessageNotReadableException(new IllegalStateException("x")),
            HttpServletResponse.SC_BAD_REQUEST), MessageResource.MESSAGE_NOT_READABLE);
        assertSingleError(resolve(httpMessageNotReadableException(null), HttpServletResponse.SC_BAD_REQUEST),
            MessageResource.MESSAGE_NOT_READABLE);
    }

    @Test
    public void testAuthenticationException() {
        assertSingleError(resolve(new BadCredentialsException("bad credentials"), HttpServletResponse.SC_UNAUTHORIZED),
            MessageResource.create("com.wavemaker.unAuthorized"), "bad credentials");
    }

    @Test
    public void testDataIntegrityViolation() {
        Exception ex = new DataIntegrityViolationException("outer", new IllegalStateException("duplicate key"));
        assertSingleError(resolve(ex, HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
            MessageResource.DATA_INTEGRITY_VIOALATION, "duplicate key");
    }

    @Test
    public void testInvalidInputException() {
        assertSingleError(resolve(new InvalidInputException("bad input"), HttpServletResponse.SC_BAD_REQUEST),
            MessageResource.INVALID_INPUT, "bad input");
        assertSingleError(
            resolve(new InvalidInputException(MessageResource.INVALID_FIELD_VALUE, "age", "negative"),
                HttpServletResponse.SC_BAD_REQUEST), MessageResource.INVALID_FIELD_VALUE, "age", "negative");
    }

    @Test
    public void testUnAuthorizedResourceAccessException() {
        assertSingleError(resolve(new UnAuthorizedResourceAccessException("no access"), HttpServletResponse.SC_FORBIDDEN),
            MessageResource.UNEXPECTED_ERROR, "no access");
        assertSingleError(
            resolve(new UnAuthorizedResourceAccessException(MessageResource.ACCESS_DENIED), HttpServletResponse.SC_FORBIDDEN),
            MessageResource.ACCESS_DENIED);
    }

    @Test
    public void testWMRuntimeException() {
        assertSingleError(resolve(new WMRuntimeException("boom"), HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
            MessageResource.UNEXPECTED_ERROR, "boom");
        assertSingleError(resolve(new WMRuntimeException(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
            MessageResource.UNEXPECTED_ERROR, "Internal Server Error");
        assertSingleError(
            resolve(new WMRuntimeException(MessageResource.INVALID_FIELD_VALUE, "age", "negative"),
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR), MessageResource.INVALID_FIELD_VALUE, "age", "negative");
    }

    @Test
    public void testWMException() {
        assertSingleError(resolve(new WMException(MessageResource.INVALID_FIELD_VALUE, "age", "negative") {
        }, HttpServletResponse.SC_INTERNAL_SERVER_ERROR), MessageResource.INVALID_FIELD_VALUE, "age", "negative");
        assertSingleError(resolve(new WMException("checked") {
        }, HttpServletResponse.SC_INTERNAL_SERVER_ERROR), MessageResource.UNEXPECTED_ERROR, "Internal Server Error");
    }

    @Test
    public void testUnknownException() {
        assertSingleError(resolve(new IllegalStateException("oops"), HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
            MessageResource.UNEXPECTED_ERROR, "oops");
        assertSingleError(resolve(new IllegalStateException(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
            MessageResource.UNEXPECTED_ERROR, "Internal Server Error");
    }

    @Test
    public void testErrorResponseMessageAndParameters() {
        ErrorResponse error = resolve(new IllegalStateException("<b>oops</b>"), HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
            .get(0);
        Assertions.assertEquals(MessageResource.UNEXPECTED_ERROR.getMessageWithPlaceholders(), error.getMessage());
        Assertions.assertEquals(List.of("<b>oops</b>"), error.getParameters());
    }

    private void setErrorResponseResolvers(List<ErrorResponseResolver> errorResponseResolvers)
        throws ReflectiveOperationException {
        Field field = ApplicationRestServiceExceptionResolver.class.getDeclaredField("errorResponseResolvers");
        field.setAccessible(true);
        field.set(resolver, errorResponseResolvers);
    }

    private MethodArgumentNotValidException methodArgumentNotValidException(BindingResult bindingResult)
        throws NoSuchMethodException {
        return new MethodArgumentNotValidException(
            new MethodParameter(Service.class.getMethod("save", User.class), 0), bindingResult);
    }

    private HttpMessageNotReadableException httpMessageNotReadableException(Throwable cause) {
        return new HttpMessageNotReadableException("not readable", cause, Mockito.mock(HttpInputMessage.class));
    }

    private List<ErrorResponse> resolve(Set<? extends ConstraintViolation<?>> violations) {
        return resolve(new ConstraintViolationException(violations), HttpServletResponse.SC_BAD_REQUEST);
    }

    private List<ErrorResponse> resolve(Exception ex, int expectedStatus) {
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        ModelAndView modelAndView = resolver.doResolveException(Mockito.mock(HttpServletRequest.class), response, null, ex);
        Mockito.verify(response).setStatus(expectedStatus);
        return ((ErrorResponses) modelAndView.getModel().get("errors")).getError();
    }

    private void assertSingleError(List<ErrorResponse> errors, String paramName, String message) {
        assertSingleError(errors, MessageResource.INVALID_FIELD_VALUE, paramName, message);
    }

    private void assertSingleError(List<ErrorResponse> errors, MessageResource messageResource, Object... parameters) {
        Assertions.assertEquals(1, errors.size());
        ErrorResponse error = errors.get(0);
        Assertions.assertEquals(messageResource.getMessageKey(), error.getMessageKey());
        Assertions.assertEquals(Arrays.asList(parameters), error.getParameters());
    }

    public static class User {
        @NotBlank
        private final String name;
        @Valid
        private final Address address;
        @Size(min = 1, max = 2)
        private final List<@NotBlank String> tags;

        public User(String name, Address address, List<String> tags) {
            this.name = name;
            this.address = address;
            this.tags = tags;
        }
    }

    public static class Address {
        @NotBlank
        private final String city;

        public Address(@NotBlank String city) {
            this.city = city;
        }
    }

    public static class JsonPayload {
        public String name;
    }

    @NotAdmin
    public static class Account {
        private final String name;

        public Account(String name) {
            this.name = name;
        }
    }

    public static class Service {

        @NotBlank
        public String greet(@NotNull String userName, int times) {
            return userName;
        }

        public void save(@Valid User user) {
        }

        @FromNotAfterTo
        public void range(int from, int to) {
        }
    }

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Constraint(validatedBy = FromNotAfterToValidator.class)
    public @interface FromNotAfterTo {

        String message() default "from must not be greater than to";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    @SupportedValidationTarget(ValidationTarget.PARAMETERS)
    public static class FromNotAfterToValidator implements ConstraintValidator<FromNotAfterTo, Object[]> {

        @Override
        public boolean isValid(Object[] parameters, ConstraintValidatorContext context) {
            return (int) parameters[0] <= (int) parameters[1];
        }
    }

    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Constraint(validatedBy = NotAdminValidator.class)
    public @interface NotAdmin {

        String message() default "must not be admin";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class NotAdminValidator implements ConstraintValidator<NotAdmin, Account> {

        @Override
        public boolean isValid(Account account, ConstraintValidatorContext context) {
            return !"admin".equals(account.name);
        }
    }
}
