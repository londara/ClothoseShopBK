package com.aurastudio.backend;

import java.nio.file.Path;

import org.springframework.boot.SpringApplication;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Runs the API on an embedded PostgreSQL 18 — no database install or password needed.
 *
 * <pre>mvn spring-boot:test-run</pre>
 *
 * Data is kept in {@code ./data/dev-postgres} between runs (delete the folder to start over).
 * The platform admin comes from ADMIN_EMAIL / ADMIN_PHONE / ADMIN_PASSWORD as usual.
 */
public class DevAuraBackendApplication {

	public static void main(String[] args) throws Exception {
		EmbeddedPostgres postgres = EmbeddedPostgres.builder()
				.setDataDirectory(Path.of("data", "dev-postgres"))
				.setCleanDataDirectory(false)
				.setPort(54329)
				.start();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				postgres.close();
			}
			catch (Exception ignored) {
				// shutting down anyway
			}
		}));
		System.setProperty("spring.datasource.url",
				"jdbc:postgresql://localhost:" + postgres.getPort() + "/postgres?stringtype=unspecified");
		System.setProperty("spring.datasource.username", "postgres");
		System.setProperty("spring.datasource.password", "");
		SpringApplication.from(AuraBackendApplication::main).run(args);
	}
}
