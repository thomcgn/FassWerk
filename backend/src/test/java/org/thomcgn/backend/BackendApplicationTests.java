package org.thomcgn.backend;

import org.thomcgn.backend.support.PostgresIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests extends PostgresIntegrationTest {

    @Test
    void contextLoads() {
    }

}
