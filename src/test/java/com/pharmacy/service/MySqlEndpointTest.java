package com.pharmacy.service;

import com.pharmacy.util.MySqlEndpoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MySqlEndpointTest {
    @Test void backupTargetMatchesJdbcHostPortAndDatabase() throws Exception {
        var endpoint=MySqlEndpoint.fromJdbcUrl("jdbc:mysql://127.0.0.1:33307/pharmacy_test?useSSL=false");
        assertEquals("127.0.0.1",endpoint.host());assertEquals("33307",endpoint.port());assertEquals("pharmacy_test",endpoint.database());
    }
    @Test void defaultPortIsMysqlPort() throws Exception {
        assertEquals("3306",MySqlEndpoint.fromJdbcUrl("jdbc:mysql://localhost/pharmacy_test").port());
    }
    @Test void missingDatabaseAndAmbiguousUrlsAreRejected() {
        assertThrows(java.io.IOException.class,()->MySqlEndpoint.fromJdbcUrl("jdbc:mysql://localhost/"));
        assertThrows(java.io.IOException.class,()->MySqlEndpoint.fromJdbcUrl("jdbc:mysql://host1,host2/pharmacy_test"));
    }
}
