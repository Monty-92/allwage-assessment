package com.allwage.clockin.service;

import com.allwage.clockin.controller.CreateSiteRequest;
import com.allwage.clockin.model.Site;
import com.allwage.clockin.model.SiteRules;
import com.allwage.clockin.store.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SiteServiceTest {

    private DocumentStore store;
    private SiteService siteService;

    @BeforeEach
    void setUp() {
        store = new DocumentStore();
        siteService = new SiteService(store);
    }

    @Test
    void createSite_persistsAndReturnsWithGeneratedId() {
        CreateSiteRequest request = new CreateSiteRequest(
                "Alpha Construction", "+27821000001",
                new SiteRules(30, List.of(), false), List.of());

        Site result = siteService.createSite(request);

        assertThat(result.id()).isNotNull().isNotBlank();
        assertThat(result.name()).isEqualTo("Alpha Construction");
        assertThat(result.managerPhoneNumber()).isEqualTo("+27821000001");
        assertThat(result.rules().toleranceMeters()).isEqualTo(30);

        Optional<Site> persisted = store.findById("sites", result.id(), Site.class);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().name()).isEqualTo("Alpha Construction");
    }

    @Test
    void createSite_emptyGeofenceList_persists() {
        CreateSiteRequest request = new CreateSiteRequest(
                "Test Site", "+27820000001",
                new SiteRules(20, List.of(), false), List.of());

        Site result = siteService.createSite(request);

        assertThat(result.geofences()).isEmpty();
    }

    @Test
    void createSite_twoSites_generatesDistinctIds() {
        Site a = siteService.createSite(new CreateSiteRequest(
                "A", "+27820000001", new SiteRules(20, List.of(), false), List.of()));
        Site b = siteService.createSite(new CreateSiteRequest(
                "B", "+27820000002", new SiteRules(20, List.of(), false), List.of()));

        assertThat(a.id()).isNotEqualTo(b.id());
    }

    @Test
    void findAll_returnsAllSites() {
        siteService.createSite(new CreateSiteRequest(
                "Site A", "+27820000001", new SiteRules(20, List.of(), false), List.of()));
        siteService.createSite(new CreateSiteRequest(
                "Site B", "+27820000002", new SiteRules(30, List.of(), false), List.of()));

        assertThat(siteService.findAll()).hasSize(2);
    }

    @Test
    void findById_existingSite_returnsPresent() {
        Site created = siteService.createSite(new CreateSiteRequest(
                "Test", "+27820000001", new SiteRules(20, List.of(), false), List.of()));

        assertThat(siteService.findById(created.id())).isPresent();
    }

    @Test
    void findById_nonExistent_returnsEmpty() {
        assertThat(siteService.findById("unknown-id")).isEmpty();
    }
}
