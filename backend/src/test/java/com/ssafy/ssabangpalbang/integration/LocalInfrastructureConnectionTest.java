package com.ssafy.ssabangpalbang.integration;

import org.apache.kafka.clients.admin.Admin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(
        named = "RUN_LOCAL_INFRA_TESTS",
        matches = "true"
)
class LocalInfrastructureConnectionTest {

    private static final String REDIS_TEST_KEY =
            "ssabangpalbang:test:infrastructure";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Test
    void PostgreSQL에_연결할_수_있다() {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT 1",
                Integer.class
        );

        assertThat(result).isEqualTo(1);
    }

    @Test
    void Redis에_값을_저장하고_조회할_수_있다() {
        try {
            redisTemplate.opsForValue().set(
                    REDIS_TEST_KEY,
                    "ok",
                    Duration.ofSeconds(30)
            );

            String result = redisTemplate.opsForValue()
                    .get(REDIS_TEST_KEY);

            assertThat(result).isEqualTo("ok");
        } finally {
            redisTemplate.delete(REDIS_TEST_KEY);
        }
    }

    @Test
    void Kafka_Broker에_연결할_수_있다() throws Exception {
        try (Admin admin = Admin.create(
                kafkaAdmin.getConfigurationProperties()
        )) {
            String clusterId = admin.describeCluster()
                    .clusterId()
                    .get(10, TimeUnit.SECONDS);

            assertThat(clusterId).isNotBlank();
        }
    }
}
