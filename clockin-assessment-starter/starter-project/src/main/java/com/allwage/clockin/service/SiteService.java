package com.allwage.clockin.service;

import com.allwage.clockin.controller.CreateSiteRequest;
import com.allwage.clockin.model.Site;
import com.allwage.clockin.store.DocumentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SiteService {

    private static final Logger log = LoggerFactory.getLogger(SiteService.class);

    private final DocumentStore store;

    public SiteService(@NonNull DocumentStore store) {
        this.store = store;
    }

    public @NonNull Site createSite(@NonNull CreateSiteRequest request) {
        String id = UUID.randomUUID().toString();
        Site site = new Site(id, request.name(), request.managerPhoneNumber(),
                request.rules(), request.geofences());
        store.save("sites", id, site);
        log.info("Site created: id={} name={}", id, request.name());
        return site;
    }

    public @NonNull List<Site> findAll() {
        return store.findAll("sites", Site.class);
    }

    public @NonNull Optional<Site> findById(@NonNull String id) {
        return store.findById("sites", id, Site.class);
    }
}
