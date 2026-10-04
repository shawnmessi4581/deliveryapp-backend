package com.deliveryapp.repository;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Spring Data parses every @Query when the app starts, so one invalid JPQL string stops the whole
 * backend from booting. This parses them all against the entity model — no database needed.
 */
class RepositoryQueriesTest {

    private static StandardServiceRegistry registry;
    private static SessionFactory sessionFactory;

    @BeforeAll
    static void buildEntityModel() throws ClassNotFoundException {
        registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .build();

        MetadataSources sources = new MetadataSources(registry);
        for (Class<?> entity : scan("com.deliveryapp.entity", new AnnotationTypeFilter(Entity.class), false)) {
            sources.addAnnotatedClass(entity);
        }
        sessionFactory = sources.buildMetadata().buildSessionFactory();
    }

    @AfterAll
    static void close() {
        if (sessionFactory != null) sessionFactory.close();
        if (registry != null) StandardServiceRegistryBuilder.destroy(registry);
    }

    @TestFactory
    List<DynamicTest> everyQueryParses() throws ClassNotFoundException {
        List<DynamicTest> tests = new ArrayList<>();
        for (Class<?> repository : scan("com.deliveryapp.repository", new AssignableTypeFilter(Repository.class), true)) {
            for (Method method : repository.getDeclaredMethods()) {
                Query query = method.getAnnotation(Query.class);
                if (query == null || query.nativeQuery()) continue;

                tests.add(DynamicTest.dynamicTest(repository.getSimpleName() + "." + method.getName(), () -> {
                    EntityManager em = sessionFactory.createEntityManager();
                    try {
                        assertDoesNotThrow(() -> em.createQuery(query.value()));
                    } finally {
                        em.close();
                    }
                }));
            }
        }
        return tests;
    }

    private static List<Class<?>> scan(String basePackage,
                                       org.springframework.core.type.filter.TypeFilter filter,
                                       boolean interfaces) throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(
                    org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
                return interfaces ? definition.getMetadata().isInterface() : definition.getMetadata().isConcrete();
            }
        };
        scanner.addIncludeFilter(filter);

        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(basePackage)) {
            classes.add(Class.forName(definition.getBeanClassName()));
        }
        return classes;
    }
}
