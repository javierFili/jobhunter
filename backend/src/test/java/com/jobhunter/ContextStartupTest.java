package com.jobhunter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole application WITHOUT a database: validates bean wiring, Spring Data derived queries, every JPQL
 * query and the entity mappings at startup. (Integration tests against MySQL are the *IT classes.)
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.sql.init.mode=never",
        "jobhunter.scheduler.enabled=false",
        "jobhunter.ai.seed-prompts=false",
        "jobhunter.security.jwt-secret=dGVzdC1vbmx5LWp3dC1zZWNyZXQtZm9yLWpvYmh1bnRlci1pbnRlZ3JhdGlvbi10ZXN0cw==",
        "jobhunter.crypto.master-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
class ContextStartupTest {

    @Autowired
    ApplicationContext context;

    @Test
    void contextStarts() {
        assertThat(context.getBeanDefinitionCount()).isGreaterThan(100);
    }
}
