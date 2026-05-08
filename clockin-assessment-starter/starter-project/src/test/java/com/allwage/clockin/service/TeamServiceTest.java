package com.allwage.clockin.service;

import com.allwage.clockin.controller.CreateSiteRequest;
import com.allwage.clockin.controller.CreateTeamRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamServiceTest {

    private DocumentStore store;
    private SiteService siteService;
    private TeamService teamService;

    @BeforeEach
    void setUp() {
        store = new DocumentStore();
        siteService = new SiteService(store);
        teamService = new TeamService(store);
    }

    @Test
    void createTeam_withValidSiteId_persistsAndReturns() {
        Site site = siteService.createSite(new CreateSiteRequest(
                "Alpha Construction", "+27821000001",
                new SiteRules(30, List.of(), false), List.of()));

        Team result = teamService.createTeam(
                new CreateTeamRequest(site.id(), "Day Shift", new TeamRules(null, null, null)));

        assertThat(result.id()).isNotNull().isNotBlank();
        assertThat(result.siteId()).isEqualTo(site.id());
        assertThat(result.name()).isEqualTo("Day Shift");
        assertThat(store.findById("teams", result.id(), Team.class)).isPresent();
    }

    @Test
    void createTeam_withUnknownSiteId_throws404() {
        assertThatThrownBy(() -> teamService.createTeam(
                new CreateTeamRequest("nonexistent-site", "Team X", new TeamRules(null, null, null))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void findAll_returnsAllTeams() {
        Site site = siteService.createSite(new CreateSiteRequest(
                "Site", "+27820000001", new SiteRules(20, List.of(), false), List.of()));
        teamService.createTeam(new CreateTeamRequest(site.id(), "Team A", new TeamRules(null, null, null)));
        teamService.createTeam(new CreateTeamRequest(site.id(), "Team B", new TeamRules(null, null, null)));

        assertThat(teamService.findAll()).hasSize(2);
    }

    @Test
    void findById_nonExistent_returnsEmpty() {
        assertThat(teamService.findById("unknown-id")).isEmpty();
    }
}
