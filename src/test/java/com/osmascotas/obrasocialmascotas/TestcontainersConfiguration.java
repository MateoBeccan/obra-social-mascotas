package com.osmascotas.obrasocialmascotas;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	static BeanFactoryPostProcessor storagePropertiesForTests() {
		return beanFactory -> {
			ConfigurableEnvironment environment = beanFactory.getBean(ConfigurableEnvironment.class);
			environment.getPropertySources().addFirst(new MapPropertySource(
					"storagePropertiesForTests",
					Map.of(
							"app.storage.endpoint", "http://localhost:9000",
							"app.storage.region", "us-east-1",
							"app.storage.access-key", "test-access",
							"app.storage.secret-key", "test-secret",
							"app.storage.bucket", "os-mascotas-test"
					)
			));
		};
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
	}

}
