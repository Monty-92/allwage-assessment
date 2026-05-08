package com.allwage.clockin.service;

import com.allwage.clockin.controller.CreateTeamRequest;
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
import java.util.Optional;
import java.util.UUID;

@Service
public class TeamService {

    private static final Logger log = LoggerFactory.getLogger(TeamService.class);

    private final DocumentStore store;

    public TeamService(@NonNull DocumentStore store) {
        this.store = store;
    }

    public @NonNull Team createTeam(@NonNull CreateTeamRequest request) {
        store.findById("sites", request.siteId(), Site.class)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Site not found: " + request.siteId()));
        String id = UUID.randomUUID().toString();
        Team team = new Team(id, request.siteId(), request.name(), request.rules());
        store.save("teams", id, team);
        log.info("Team created: id={} siteId={} name={}", id, request.siteId(), request.name());
        return team;
    }

    public @NonNull List<Team> findAll() {
        return store.findAll("teams", Team.class);
    }

    public @NonNull Optional<Team> findById(@NonNull String id) {
        return store.findById("teams", id, Team.class);
    }
}
