package com.bloom.app;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("deployment")
@AutoConfigureMockMvc
@SpringBootTest
class DeploymentProfileStartupTest {

    private static final PostgreSQLContainer<?> POSTGRES = startPostgres();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Autowired
    private Flyway flyway;

    @DynamicPropertySource
    static void deploymentProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("logging.file.name", () -> "target/deployment-profile-test.log");
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @Test
    void startsWithValidatedSchemaAndExposesOnlySafeAnonymousHealth() throws Exception {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
            .isEqualTo("validate");
        assertThat(environment.getProperty("spring.jpa.show-sql"))
            .isEqualTo("false");
        assertThat(environment.getProperty("logging.level.org.hibernate.orm.jdbc.bind"))
            .isEqualTo("OFF");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("22");

        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"status\":\"UP\"}", true));

        mockMvc.perform(get("/api/items"))
            .andExpect(status().isUnauthorized());
    }

    private static PostgreSQLContainer<?> startPostgres() {
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
        postgres.start();
        return postgres;
    }
}
