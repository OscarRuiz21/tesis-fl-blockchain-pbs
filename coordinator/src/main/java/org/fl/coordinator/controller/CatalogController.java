package org.fl.coordinator.controller;

import lombok.RequiredArgsConstructor;
import org.fl.coordinator.service.CatalogService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** {@code GET /catalog}: opciones válidas del experimento, para que el portal no las duplique. */
@RestController
@RequestMapping("/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalog;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CatalogResponse> catalog() {
        return ResponseEntity.ok(new CatalogResponse(
            catalog.models(),
            catalog.aggregations(),
            catalog.anonymityModes(),
            catalog.partitionStrategies(),
            catalog.datasetTypes()));
    }

    /** Cada lista va en orden alfabético. */
    public record CatalogResponse(
        List<String> models,
        List<String> aggregations,
        List<String> anonymityModes,
        List<String> partitionStrategies,
        List<String> datasetTypes
    ) {}
}
