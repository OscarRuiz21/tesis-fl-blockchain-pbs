package org.fl.coordinator.controller;

import org.fl.coordinator.service.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests del {@code CatalogController}. Carga el {@link CatalogService} real
 * (no se mockea) para validar que la composición concreta de las whitelists
 * funciona end-to-end.
 */
@WebMvcTest(controllers = CatalogController.class)
@Import({CatalogService.class, GlobalExceptionHandler.class})
class CatalogControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void returnsAllCatalogsSortedAlphabetically() throws Exception {
        mvc.perform(get("/catalog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.models[0]").value("logistic_regression"))
            .andExpect(jsonPath("$.models[1]").value("mlp"))
            .andExpect(jsonPath("$.aggregations[0]").value("fedavg"))
            .andExpect(jsonPath("$.aggregations[1]").value("fedmedian"))
            .andExpect(jsonPath("$.aggregations[2]").value("fedprox"))
            .andExpect(jsonPath("$.anonymityModes[0]").value("none"))
            .andExpect(jsonPath("$.anonymityModes[1]").value("pbs"))
            .andExpect(jsonPath("$.partitionStrategies[0]").value("dirichlet"))
            .andExpect(jsonPath("$.partitionStrategies[1]").value("iid"))
            .andExpect(jsonPath("$.datasetTypes[0]").value("tabular_csv"));
    }

    @Test
    void hasExpectedCardinalities() throws Exception {
        mvc.perform(get("/catalog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.models.length()").value(2))
            .andExpect(jsonPath("$.aggregations.length()").value(3))
            .andExpect(jsonPath("$.anonymityModes.length()").value(2))
            .andExpect(jsonPath("$.partitionStrategies.length()").value(2))
            .andExpect(jsonPath("$.datasetTypes.length()").value(1));
    }
}
