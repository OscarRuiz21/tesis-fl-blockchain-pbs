package org.fl.coordinator.service;

import org.springframework.stereotype.Service;

import java.util.List;

/** Opciones válidas para {@code GET /catalog}; salen de las whitelists de {@link SpecValidator}. */
@Service
public class CatalogService {

    public List<String> models() {
        return sortedList(SpecValidator.allowedModels());
    }

    public List<String> aggregations() {
        return sortedList(SpecValidator.allowedAggregations());
    }

    public List<String> anonymityModes() {
        return sortedList(SpecValidator.allowedAnonymityModes());
    }

    public List<String> partitionStrategies() {
        return sortedList(SpecValidator.allowedPartitionStrategies());
    }

    // Debe coincidir con DATASET_LOADERS en python/datasets/__init__.py.
    public List<String> datasetTypes() {
        return List.of("tabular_csv");
    }

    private static List<String> sortedList(java.util.Set<String> set) {
        return set.stream().sorted().toList();
    }
}
