package com.allwage.clockin.service;

import com.allwage.clockin.controller.CreateSiteRequest;
import com.allwage.clockin.model.Site;
import com.allwage.clockin.store.DocumentStore;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SiteService {

    private final DocumentStore store;

    public SiteService(@NonNull DocumentStore store) {
        this.store = store;
    }

    public @NonNull Site createSite(@NonNull CreateSiteRequest request) {
        throw new UnsupportedOperationException("not implemented");
    }

    public @NonNull List<Site> findAll() {
        throw new UnsupportedOperationException("not implemented");
    }

    public @NonNull Optional<Site> findById(@NonNull String id) {
        throw new UnsupportedOperationException("not implemented");
    }
}
