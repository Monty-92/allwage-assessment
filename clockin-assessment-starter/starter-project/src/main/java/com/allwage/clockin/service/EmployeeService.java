package com.allwage.clockin.service;

import com.allwage.clockin.controller.CreateEmployeeRequest;
import com.allwage.clockin.model.Employee;
import com.allwage.clockin.model.Site;
import com.allwage.clockin.model.Team;
import com.allwage.clockin.store.DocumentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    private final DocumentStore store;

    public EmployeeService(@NonNull DocumentStore store) {
        this.store = store;
    }

    public @NonNull Employee createEmployee(@NonNull CreateEmployeeRequest request) {
        validateEnrollments(request.siteEnrollments());
        String id = UUID.randomUUID().toString();
        Employee employee = new Employee(id, request.name(), request.phoneNumber(),
                request.siteEnrollments(), request.ruleOverrides());
        store.save("employees", id, employee);
        log.info("Employee created: id={} name={}", id, request.name());
        return employee;
    }

    public @NonNull List<Employee> findAll() {
        return store.findAll("employees", Employee.class);
    }

    public @NonNull Optional<Employee> findById(@NonNull String id) {
        return store.findById("employees", id, Employee.class);
    }

    private void validateEnrollments(@NonNull Map<String, String> enrollments) {
        for (Map.Entry<String, String> entry : enrollments.entrySet()) {
            String siteId = entry.getKey();
            String teamId = entry.getValue();
            store.findById("sites", siteId, Site.class)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Site not found: " + siteId));
            Team team = store.findById("teams", teamId, Team.class)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Team not found: " + teamId));
            if (!siteId.equals(team.siteId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Team " + teamId + " does not belong to site " + siteId);
            }
        }
    }
}
