package com.allwage.clockin.controller;

import com.allwage.clockin.model.ClockEvent;
import com.allwage.clockin.store.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClockControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DocumentStore store;

    @BeforeEach
    void setUp() {
        store.clearCollection("clocks");
    }

    @Test
    void clockIn_savesToStore() {
        String requestBody = """
            {
                "employeeId": "emp-123",
                "timestamp": "2024-01-15T09:00:00+02:00",
                "latitude": -26.2041,
                "longitude": 28.0473,
                "accuracyMeters": 10.0,
                "type": "IN"
            }
            """;

        ResponseEntity<ClockEvent> response = restTemplate.postForEntity(
            "/api/clocks",
            new org.springframework.http.HttpEntity<>(
                requestBody,
                createJsonHeaders()
            ),
            ClockEvent.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().employeeId()).isEqualTo("emp-123");
        assertThat(response.getBody().type()).isEqualTo(ClockEvent.ClockType.IN);

        // Verify it was saved to the store
        List<ClockEvent> stored = store.findAll("clocks", ClockEvent.class);
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().employeeId()).isEqualTo("emp-123");
    }

    private org.springframework.http.HttpHeaders createJsonHeaders() {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }
}
