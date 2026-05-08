# ADR-001 — Embed Geofences and Rules Inside Parent Documents

**Status:** Accepted  
**Date:** 2026-05-08  
**Deciders:** Candidate

---

## Context

The `DocumentStore` is a `ConcurrentHashMap<String, Map<String, Object>>`. It has no join capability — there is no `findBy` predicate that scans a foreign-key relationship without a full collection scan. The two main embedded-vs-separate decisions were:

1. **Geofences** — should they live in a `geofences` collection keyed by `geofenceId`, or be embedded in the parent `Site` document?
2. **Rules** — `SiteRules`, `TeamRules`, and `EmployeeRules` could each be a top-level collection, or be embedded in their owning document.

---

## Decision

**Embed all of them.**

- `Geofence` objects are embedded as `List<Geofence>` inside `Site`.
- `SiteRules` is embedded in `Site`.
- `TeamRules` is embedded in `Team`.
- `EmployeeRules` is embedded in `Employee.ruleOverrides`.

---

## Consequences

### Positive

- **Single-read access.** Loading a `Site` gives all geofences and rules in one `DocumentStore.findById("sites", siteId)` call. No secondary lookups.
- **Atomic consistency.** There is no window in which a geofence exists without its parent site, or vice versa.
- **Simpler validation.** `GeofenceValidator` receives a fully-populated `Site` object — no lazy loading or null checks for missing associations.

### Negative / Accepted

- **No independent geofence queries.** Listing all geofences across all sites requires iterating the sites collection. No in-scope use case requires this.
- **Document size.** A site with many geofences produces a larger document. Acceptable for seed-data volumes.

---

## Rejected Alternative

**Separate `geofences` collection, keyed by `geofenceId`.**

- Would require `findAll("geofences", Geofence.class)` and a filter on `siteId` (O(n) scan) to load geofences for a single site.
- Adds a read dependency that does not exist in the embedded model.
- No access pattern in this system ever needs a geofence without its site.
