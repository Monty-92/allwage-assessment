package com.allwage.clockin.controller;

import com.allwage.clockin.model.Employee;
import com.allwage.clockin.model.Site;
import com.allwage.clockin.model.Team;
import com.allwage.clockin.service.EmployeeService;
import com.allwage.clockin.service.SiteService;
import com.allwage.clockin.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the Management API.
 * Provides POST (create) and GET (read) operations for sites, teams, and employees.
 * Deliberately excludes PATCH/DELETE — create + read is sufficient to seed runtime data.
 */
@RestController
public class ManagementController {

    private final SiteService siteService;
    private final TeamService teamService;
    private final EmployeeService employeeService;

    public ManagementController(@NonNull SiteService siteService,
                                @NonNull TeamService teamService,
                                @NonNull EmployeeService employeeService) {
        this.siteService = siteService;
        this.teamService = teamService;
        this.employeeService = employeeService;
    }

    // --- Sites ---

    @PostMapping("/api/sites")
    public @NonNull ResponseEntity<Site> createSite(
            @Valid @RequestBody @NonNull CreateSiteRequest request) {
        throw new UnsupportedOperationException("not implemented");
    }

    @GetMapping("/api/sites")
    public @NonNull ResponseEntity<List<Site>> getAllSites() {
        throw new UnsupportedOperationException("not implemented");
    }

    @GetMapping("/api/sites/{id}")
    public @NonNull ResponseEntity<Site> getSiteById(@PathVariable @NonNull String id) {
        throw new UnsupportedOperationException("not implemented");
    }

    // --- Teams ---

    @PostMapping("/api/teams")
    public @NonNull ResponseEntity<Team> createTeam(
            @Valid @RequestBody @NonNull CreateTeamRequest request) {
        throw new UnsupportedOperationException("not implemented");
    }

    @GetMapping("/api/teams")
    public @NonNull ResponseEntity<List<Team>> getAllTeams() {
        throw new UnsupportedOperationException("not implemented");
    }

    @GetMapping("/api/teams/{id}")
    public @NonNull ResponseEntity<Team> getTeamById(@PathVariable @NonNull String id) {
        throw new UnsupportedOperationException("not implemented");
    }

    // --- Employees ---

    @PostMapping("/api/employees")
    public @NonNull ResponseEntity<Employee> createEmployee(
            @Valid @RequestBody @NonNull CreateEmployeeRequest request) {
        throw new UnsupportedOperationException("not implemented");
    }

    @GetMapping("/api/employees")
    public @NonNull ResponseEntity<List<Employee>> getAllEmployees() {
        throw new UnsupportedOperationException("not implemented");
    }

    @GetMapping("/api/employees/{id}")
    public @NonNull ResponseEntity<Employee> getEmployeeById(@PathVariable @NonNull String id) {
        throw new UnsupportedOperationException("not implemented");
    }
}
