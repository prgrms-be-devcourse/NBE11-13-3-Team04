package com.example.iter.support;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/** 실제 MySQL과 Redis를 재시작하지 않고 테스트 데이터만 격리한다. */
public final class DatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    public void clean() {
        cleanMySql();
        cleanRedis();
    }

    private void cleanMySql() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            List<String> tableNames = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT table_name FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'")) {
                try (ResultSet resultSet = query.executeQuery()) {
                    while (resultSet.next()) {
                        tableNames.add(resultSet.getString(1));
                    }
                }
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                try {
                    for (String tableName : tableNames) {
                        // 이름은 information_schema에서 읽지만 식별자 escape도 적용한다.
                        statement.execute("TRUNCATE TABLE `" + tableName.replace("`", "``") + "`");
                    }
                } finally {
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
            return null;
        });
    }

    private void cleanRedis() {
        var connectionFactory = Objects.requireNonNull(redisTemplate.getConnectionFactory());
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }
}
