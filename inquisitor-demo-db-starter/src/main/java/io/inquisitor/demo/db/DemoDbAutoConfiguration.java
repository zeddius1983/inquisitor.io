package io.inquisitor.demo.db;

import lombok.val;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;

@AutoConfiguration(before = DataSourceAutoConfiguration.class)
@ConditionalOnClass(DataSource.class)
public class DemoDbAutoConfiguration {

    @Bean
    @Profile({"local", "unitTest"})
    @ConditionalOnMissingBean
    PostgreSQLContainer postgresContainer() {
        val container = new PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName("inquisitor_demo")
                .withUsername("demo")
                .withPassword("demo")
                .withReuse(true);
        container.start();
        return container;
    }

    @Bean
    @Profile({"local", "unitTest"})
    @ConditionalOnMissingBean(JdbcConnectionDetails.class)
    JdbcConnectionDetails jdbcConnectionDetailsLocal(PostgreSQLContainer container) {
        return new ContainerJdbcConnectionDetails(container);
    }

    // ── other profiles: no explicit bean ─────────────────────────────────────
    // Spring Boot's DataSourceAutoConfiguration reads spring.datasource.* directly.

    private record ContainerJdbcConnectionDetails(PostgreSQLContainer container)
            implements JdbcConnectionDetails {
        @Override public String getJdbcUrl()        { return container.getJdbcUrl(); }
        @Override public String getUsername()        { return container.getUsername(); }
        @Override public String getPassword()        { return container.getPassword(); }
        @Override public String getDriverClassName() { return container.getDriverClassName(); }
    }
}
