package com.allwage.clockin.service;

import com.allwage.clockin.controller.CreateTeamRequest;
import com.allwage.clockin.model.Team;
import com.allwage.clockin.store.DocumentStore;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TeamService {

    private final DocumentStore store;

    public TeamService(@NonNull DocumentStore store) {
        this.store = store;
    }

    public @NonNull Team createTeam(@NonNull CreateTeamRequest request) {
        throw new UnsupportedOperationException("not implemented");
    }

    public @NonNull List<Team> findAll() {
        throw new UnsupportedOperationException("not implemented");
    }

    public @NonNull Optional<Team> findById(@NonNull String id) {
        throw new UnsupportedOperationException("not implemented");
    }
}
