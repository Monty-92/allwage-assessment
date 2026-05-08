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
        Site site = siteService.createSite(request);
        return ResponseEntity
                .created(resourceUri(site.id()))
                .body(site);
    }

    @GetMapping("/api/sites")
    public @NonNull ResponseEntity<List<Site>> getAllSites() {
        return ResponseEntity.ok(siteService.findAll());
    }

    @GetMapping("/api/sites/{id}")
    public @NonNull ResponseEntity<Site> getSiteById(@PathVariable @NonNull String id) {
        return siteService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // --- Teams ---

    @PostMapping("/api/teams")
    public @NonNull ResponseEntity<Team> createTeam(
            @Valid @RequestBody @NonNull CreateTeamRequest request) {
        Team team = teamService.createTeam(request);
        return ResponseEntity
                .created(resourceUri(team.id()))
                .body(team);
    }

    @GetMapping("/api/teams")
    public @NonNull ResponseEntity<List<Team>> getAllTeams() {
        return ResponseEntity.ok(teamService.findAll());
    }

    @GetMapping("/api/teams/{id}")
    public @NonNull ResponseEntity<Team> getTeamById(@PathVariable @NonNull String id) {
        return teamService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // --- Employees ---

    @PostMapping("/api/employees")
    public @NonNull ResponseEntity<Employee> createEmployee(
            @Valid @RequestBody @NonNull CreateEmployeeRequest request) {
        Employee employee = employeeService.createEmployee(request);
        return ResponseEntity
                .created(resourceUri(employee.id()))
                .body(employee);
    }

    @GetMapping("/api/employees")
    public @NonNull ResponseEntity<List<Employee>> getAllEmployees() {
        return ResponseEntity.ok(employeeService.findAll());
    }

    @GetMapping("/api/employees/{id}")
    public @NonNull ResponseEntity<Employee> getEmployeeById(@PathVariable @NonNull String id) {
        return employeeService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private java.net.URI resourceUri(String id) {
        return org.springframework.web.servlet.support.ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }
}
