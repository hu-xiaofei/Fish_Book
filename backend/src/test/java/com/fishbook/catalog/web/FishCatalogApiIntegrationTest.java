package com.fishbook.catalog.web;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fishbook.support.MySqlTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class FishCatalogApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void catalogSearchReturnsTheSeededFirstPage() throws Exception {
        mvc.perform(get("/api/v1/fish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(12))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.totalItems").value(12));
    }

    @Test
    void catalogSearchFindsChineseAlias() throws Exception {
        mvc.perform(get("/api/v1/fish").param("q", "黑鱼"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("channa-argus"));
    }

    @Test
    void catalogSearchFindsCaseInsensitiveScientificName() throws Exception {
        mvc.perform(get("/api/v1/fish").param("q", "cHaNnA ArGuS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].slug").value("channa-argus"));
    }

    @Test
    void catalogSearchCombinesFamilyAndHabitatFilters() throws Exception {
        mvc.perform(get("/api/v1/fish")
                        .param("family", "鳢科").param("habitat", "LAKE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].slug").value(hasItem("channa-argus")));
    }

    @Test
    void catalogFiltersExposeFamiliesAndAllHabitatOptions() throws Exception {
        mvc.perform(get("/api/v1/fish/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.families").isArray())
                .andExpect(jsonPath("$.habitats.length()").value(5));
    }

    @Test
    void catalogDetailIncludesImageAttribution() throws Exception {
        mvc.perform(get("/api/v1/fish/channa-argus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commonNameZh").value("乌鳢"))
                .andExpect(jsonPath("$.image.sourceUrl").isNotEmpty())
                .andExpect(jsonPath("$.image.licenseUrl").isNotEmpty());
    }

    @Test
    void invalidCatalogRequestsReturnStableSafeErrors() throws Exception {
        mvc.perform(get("/api/v1/fish").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATALOG_QUERY"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        mvc.perform(get("/api/v1/fish").param("size", "12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATALOG_QUERY"));
        mvc.perform(get("/api/v1/fish").param("q", "鱼".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATALOG_QUERY"));
        mvc.perform(get("/api/v1/fish").param("habitat", "SEA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATALOG_QUERY"));
        mvc.perform(get("/api/v1/fish/missing-fish"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FISH_NOT_FOUND"));
        mvc.perform(get("/api/v1/fish/Bad_Slug"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATALOG_QUERY"));
    }
}
