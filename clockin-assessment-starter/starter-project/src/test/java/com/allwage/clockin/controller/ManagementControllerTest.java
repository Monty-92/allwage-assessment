package com.allwage.clockin.controller;

import com.allwage.clockin.model.Employee;
import com.allwage.clockin.model.Site;
import com.allwage.clockin.model.SiteRules;
import com.allwage.clockin.model.Team;
import com.allwage.clockin.model.TeamRules;
import com.allwage.clockin.store.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.seed-data.enabled=false")
class ManagementControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DocumentStore store;

    @BeforeEach
    void setUp() {
        store.clearCollection("sites");
        store.clearCollection("teams");
        store.clearCollection("employees");
    }

    // --------------------------------------------------------
    // Sites
    // --------------------------------------------------------

    @Test
    void postSite_validRequest_returns201WithLocationHeader() {
        ResponseEntity<Site> response = restTemplate.postForEntity(
                "/api/sites", new HttpEntity<>(validSiteRequest(), jsonHeaders()), Site.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull().isNotBlank();
        assertThat(response.getBody().name()).isEqualTo("Test Site");
        assertThat(response.getHeaders().getLocation()).isNotNull();
    }

    @Test
    void postSite_missingName_returns400() {
        String json = """
                {
                    "managerPhoneNumber": "+27820000001",
                    "rules": { "toleranceMeters": 20, "strictModeWindows": [], "approvalRequired": false },
                    "geofences": []
                }
                """;
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/sites", new HttpEntity<>(json, jsonHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getSites_afterCreation_includesNewSite() {
        restTemplate.postForEntity("/api/sites",
                new HttpEntity<>(validSiteRequest(), jsonHeaders()), Site.class);

        ResponseEntity<Site[]> response = restTemplate.getForEntity("/api/sites", Site[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void getSiteById_exists_returns200() {
        ResponseEntity<Site> created = restTemplate.postForEntity(
                "/api/sites", new HttpEntity<>(validSiteRequest(), jsonHeaders()), Site.class);
        String id = created.getBody().id();

        ResponseEntity<Site> response = restTemplate.getForEntity("/api/sites/" + id, Site.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(id);
    }

    @Test
    void getSiteById_notFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/sites/nonexistent", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --------------------------------------------------------
    // Teams
    // --------------------------------------------------------

    @Test
    void postTeam_validSiteId_returns201() {
        String siteId = createSiteAndGetId();

        ResponseEntity<Team> response = restTemplate.postForEntity("/api/teams",
                new HttpEntity<>(new CreateTeamRequest(siteId, "Day Shift", new TeamRules(null, null, null)), jsonHeaders()),
                Team.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().siteId()).isEqualTo(siteId);
        assertThat(response.getHeaders().getLocation()).isNotNull();
    }

    @Test
    void postTeam_unknownSiteId_returns404() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/teams",
                new HttpEntity<>(new CreateTeamRequest("nonexistent-site", "Team X", new TeamRules(null, null, null)), jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getTeamById_notFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/teams/nonexistent", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --------------------------------------------------------
    // Employees
    // --------------------------------------------------------

    @Test
    void postEmployee_validEnrollments_returns201() {
        String siteId = createSiteAndGetId();
        String teamId = createTeamAndGetId(siteId);

        ResponseEntity<Employee> response = restTemplate.postForEntity("/api/employees",
                new HttpEntity<>(new CreateEmployeeRequest("Alice", "+27820000002", Map.of(siteId, teamId), null), jsonHeaders()),
                Employee.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().name()).isEqualTo("Alice");
        assertThat(response.getHeaders().getLocation()).isNotNull();
    }

    @Test
    void postEmployee_unknownTeam_returns404() {
        String siteId = createSiteAndGetId();

        ResponseEntity<String> response = restTemplate.postForEntity("/api/employees",
                new HttpEntity<>(new CreateEmployeeRequest("Bob", "+27820000003", Map.of(siteId, "nonexistent-team"), null), jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getEmployeeById_notFound_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/employees/nonexistent", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --------------------------------------------------------
    // Helpers
    // --------------------------------------------------------

    private String createSiteAndGetId() {
        ResponseEntity<Site> response = restTemplate.postForEntity(
                "/api/sites", new HttpEntity<>(validSiteRequest(), jsonHeaders()), Site.class);
        return response.getBody().id();
    }

    private String createTeamAndGetId(String siteId) {
        ResponseEntity<Team> response = restTemplate.postForEntity("/api/teams",
                new HttpEntity<>(new CreateTeamRequest(siteId, "Test Team", new TeamRules(null, null, null)), jsonHeaders()),
                Team.class);
        return response.getBody().id();
    }

    private CreateSiteRequest validSiteRequest() {
        return new CreateSiteRequest(
                "Test Site", "+27820000001",
                new SiteRules(20, List.of(), false), List.of());
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
