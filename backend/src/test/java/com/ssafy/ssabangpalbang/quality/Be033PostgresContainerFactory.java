package com.ssafy.ssabangpalbang.quality;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.util.UUID;

public final class Be033PostgresContainerFactory {

    private static final DockerImageName IMAGE = buildImage();

    private Be033PostgresContainerFactory() {
    }

    public static PostgreSQLContainer<?> create() {
        return new PostgreSQLContainer<>(IMAGE)
                .withDatabaseName("ssabangpalbang_be033")
                .withUsername("test")
                .withPassword("test");
    }

    private static DockerImageName buildImage() {
        String imageName = "ssabangpalbang-postgres-test-"
                + UUID.randomUUID().toString().replace("-", "");
        String imageId = new ImageFromDockerfile(imageName, true)
                .withDockerfile(Path.of("..", "infra", "postgres", "Dockerfile"))
                .get();
        return DockerImageName.parse(imageId).asCompatibleSubstituteFor("postgres");
    }
}
