package com.allwage.clockin.service;

import com.allwage.clockin.controller.CreateEmployeeRequest;
import com.allwage.clockin.controller.CreateSiteRequest;
import com.allwage.clockin.controller.CreateTeamRequest;
import com.allwage.clockin.model.Employee;
import com.allwage.clockin.model.Site;
import com.allwage.clockin.model.SiteRules;
import com.allwage.clockin.model.Team;
import com.allwage.clockin.model.TeamRules;
import com.allwage.clockin.store.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmployeeServiceTest {

    private DocumentStore store;
    private SiteService siteService;
    private TeamService teamService;
    private EmployeeService employeeService;

    @BeforeEach
    void setUp() {
        store = new DocumentStore();
        siteService = new SiteService(store);
        teamService = new TeamService(store);
        employeeService = new EmployeeService(store);
    }

    private Site createTestSite() {
        return siteService.createSite(new CreateSiteRequest(
                "Test Site", "+27820000001", new SiteRules(20, List.of(), false), List.of()));
    }

    private Team createTestTeam(String siteId) {
        return teamService.createTeam(
                new CreateTeamRequest(siteId, "Test Team", new TeamRules(null, null, null)));
    }

    @Test
    void createEmployee_withValidEnrollments_persists() {
        Site site = createTestSite();
        Team team = createTestTeam(site.id());

        Employee result = employeeService.createEmployee(new CreateEmployeeRequest(
                "Alice", "+27820000002", Map.of(site.id(), team.id()), null));

        assertThat(result.id()).isNotNull().isNotBlank();
        assertThat(result.name()).isEqualTo("Alice");
        assertThat(result.siteEnrollments()).containsEntry(site.id(), team.id());
        assertThat(store.findById("employees", result.id(), Employee.class)).isPresent();
    }

    @Test
    void createEmployee_unknownSiteInEnrollment_throws404() {
        Site site = createTestSite();
        Team team = createTestTeam(site.id());

        assertThatThrownBy(() -> employeeService.createEmployee(new CreateEmployeeRequest(
                "Bob", "+27820000003", Map.of("nonexistent-site", team.id()), null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createEmployee_unknownTeamInEnrollment_throws404() {
        Site site = createTestSite();

        assertThatThrownBy(() -> employeeService.createEmployee(new CreateEmployeeRequest(
                "Carol", "+27820000004", Map.of(site.id(), "nonexistent-team"), null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createEmployee_teamDoesNotBelongToSite_throws404() {
        Site site1 = createTestSite();
        Site site2 = siteService.createSite(new CreateSiteRequest(
                "Other Site", "+27820000009", new SiteRules(20, List.of(), false), List.of()));
        Team teamForSite2 = createTestTeam(site2.id());

        // Enroll at site1 but with a team that belongs to site2 — invalid cross-site enrollment
        assertThatThrownBy(() -> employeeService.createEmployee(new CreateEmployeeRequest(
                "Dave", "+27820000005", Map.of(site1.id(), teamForSite2.id()), null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createEmployee_nullRuleOverrides_persistsCleanly() {
        Site site = createTestSite();
        Team team = createTestTeam(site.id());

        Employee result = employeeService.createEmployee(new CreateEmployeeRequest(
                "Eve", "+27820000006", Map.of(site.id(), team.id()), null));

        assertThat(result.ruleOverrides()).isNull();
    }
}
