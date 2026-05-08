package com.allwage.clockin.store;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Simple in-memory document store.
 *
 * This simulates a NoSQL document database. Data is stored as documents
 * organised into collections. Think of it like a Map of Maps.
 *
 * Note: Data is lost when the application restarts. This is intentional
 * for the assessment - focus on your design, not persistence setup.
 */
@Component
public class DocumentStore {

    private final Map<String, Map<String, Object>> collections = new ConcurrentHashMap<>();

    /**
     * Save a document to a collection.
     *
     * @param collection The collection name (e.g., "clocks", "employees")
     * @param id         The document ID
     * @param document   The document to store
     */
    public <T> void save(@NonNull String collection, @NonNull String id, @NonNull T document) {
        collections
            .computeIfAbsent(collection, k -> new ConcurrentHashMap<>())
            .put(id, document);
    }

    /**
     * Find a document by ID.
     *
     * @param collection The collection name
     * @param id         The document ID
     * @param type       The expected document type
     * @return The document if found
     */
    @SuppressWarnings("unchecked")
    public @NonNull <T> Optional<T> findById(@NonNull String collection, @NonNull String id, @NonNull Class<T> type) {
        Map<String, Object> col = collections.get(collection);
        if (col == null) {
            return Optional.empty();
        }
        Object doc = col.get(id);
        if (doc == null || !type.isInstance(doc)) {
            return Optional.empty();
        }
        return Optional.of((T) doc);
    }

    /**
     * Find all documents in a collection.
     *
     * @param collection The collection name
     * @param type       The expected document type
     * @return All documents in the collection
     */
    @SuppressWarnings("unchecked")
    public @NonNull <T> List<T> findAll(@NonNull String collection, @NonNull Class<T> type) {
        Map<String, Object> col = collections.get(collection);
        if (col == null) {
            return List.of();
        }
        return col.values().stream()
            .filter(type::isInstance)
            .map(doc -> (T) doc)
            .collect(Collectors.toList());
    }

    /**
     * Delete a document by ID.
     *
     * @param collection The collection name
     * @param id         The document ID
     * @return true if the document was deleted, false if it didn't exist
     */
    public boolean delete(@NonNull String collection, @NonNull String id) {
        Map<String, Object> col = collections.get(collection);
        if (col == null) {
            return false;
        }
        return col.remove(id) != null;
    }

    /**
     * Clear all documents from a collection.
     *
     * @param collection The collection name
     */
    public void clearCollection(@NonNull String collection) {
        collections.remove(collection);
    }
}
