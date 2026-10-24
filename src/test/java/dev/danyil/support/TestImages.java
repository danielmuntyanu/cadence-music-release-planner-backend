package dev.danyil.support;

import org.testcontainers.utility.DockerImageName;

/**
 * Docker images used by integration tests, pinned in one place.
 * Bump a version here and every test picks it up.
 */
public final class TestImages {

    private TestImages() {}

    public static final String POSTGRES = "postgres:17-alpine";

    // Upstream MinIO no longer publishes images; pgsty/minio is a community rebuild from the same source
    public static final DockerImageName MINIO = DockerImageName.parse("pgsty/minio:RELEASE.2026-08-04T00-00-00Z");
}