package com.allwage.clockin.service;

import com.allwage.clockin.controller.CreateEmployeeRequest;
import com.allwage.clockin.model.Employee;
import com.allwage.clockin.store.DocumentStore;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EmployeeService {

    private final DocumentStore store;

    public EmployeeService(@NonNull DocumentStore store) {
        this.store = store;
    }

    public @NonNull Employee createEmployee(@NonNull CreateEmployeeRequest request) {
        throw new UnsupportedOperationException("not implemented");
    }

    public @NonNull List<Employee> findAll() {
        throw new UnsupportedOperationException("not implemented");
    }

    public @NonNull Optional<Employee> findById(@NonNull String id) {
        throw new UnsupportedOperationException("not implemented");
    }
}
