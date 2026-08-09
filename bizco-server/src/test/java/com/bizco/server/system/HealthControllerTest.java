package com.bizco.server.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bizco.common.api.HealthResponse;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerTest {

    @Test
    void healthReturnsUpWhenDatabaseConnectionIsValid() {
        final HealthResponse response = new HealthController(new StubDataSource(true)).health();

        assertEquals("UP", response.status());
        assertEquals("UP", response.databaseStatus());
        assertNotNull(response.checkedAt());
    }

    @Test
    void healthReturnsDownWhenDatabaseConnectionIsInvalid() {
        final HealthResponse response = new HealthController(new StubDataSource(false)).health();

        assertEquals("DOWN", response.status());
        assertEquals("DOWN", response.databaseStatus());
        assertEquals("Database connection could not be validated.", response.message());
    }

    @Test
    void api001HealthIsAvailableUnderVersionedBasePath() throws Exception {
        MockMvcBuilders.standaloneSetup(new HealthController(new StubDataSource(true))).build()
                .perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private static class StubDataSource implements DataSource {

        private final boolean valid;

        StubDataSource(final boolean valid) {
            this.valid = valid;
        }

        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "isValid" -> valid;
                        case "close" -> null;
                        case "isClosed" -> false;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        @Override
        public Connection getConnection(final String username, final String password) {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(final PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(final int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(final Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper.");
        }

        @Override
        public boolean isWrapperFor(final Class<?> iface) {
            return false;
        }
    }
}
