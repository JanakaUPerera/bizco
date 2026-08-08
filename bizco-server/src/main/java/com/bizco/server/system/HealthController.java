package com.bizco.server.system;

import com.bizco.common.api.HealthResponse;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private static final int DATABASE_VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public HealthController(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(DATABASE_VALIDATION_TIMEOUT_SECONDS)) {
                return HealthResponse.up("Server and database are ready.");
            }
            return HealthResponse.down("DOWN", "Database connection could not be validated.");
        } catch (final SQLException exception) {
            return HealthResponse.down("DOWN", "Database connection failed: " + exception.getMessage());
        }
    }
}
