package com.fishbook.administration.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.fishbook.support.MySqlTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestConfiguration.class)
class AdminFishApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void createsEditsPublishesAndUnpublishesFish() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/admin/fishes")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(validCreateJson("api-test-fish")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/v1/admin/fishes/\\d+")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        long id = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        mvc.perform(put("/api/v1/admin/fishes/{id}", id)
                        .with(csrf()).contentType(APPLICATION_JSON)
                        .content(validUpdateJson("更新名称")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("api-test-fish"))
                .andExpect(jsonPath("$.commonNameZh").value("更新名称"))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mvc.perform(post("/api/v1/admin/fishes/{id}/publish", id).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(post("/api/v1/admin/fishes/{id}/unpublish", id).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNPUBLISHED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchesDetailsAndRejectsFixedSizeAndInvalidIds() throws Exception {
        long id = create("admin-search-fish");

        mvc.perform(get("/api/v1/admin/fishes")
                        .param("q", "admin-search")
                        .param("status", "draft")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
        mvc.perform(get("/api/v1/admin/fishes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagePath").value("/images/fish/admin-search-fish.jpg"));
        mvc.perform(get("/api/v1/admin/fishes").param("size", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATALOG_QUERY"));
        mvc.perform(get("/api/v1/admin/fishes/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATALOG_QUERY"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void mapsRequestValidationAndDomainValidationToSafeBadRequests() throws Exception {
        mvc.perform(post("/api/v1/admin/fishes")
                        .with(csrf()).contentType(APPLICATION_JSON)
                        .content(validCreateJson("invalid-content-fish")
                                .replace("\"commonNameZh\":\"管理鱼invalid-content-fish\"", "\"commonNameZh\":\"\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("commonNameZh"));
        mvc.perform(post("/api/v1/admin/fishes")
                        .with(csrf()).contentType(APPLICATION_JSON)
                        .content(validCreateJson("duplicate-alias-fish")
                                .replace("\"aliases\":[\"管理别名\"]", "\"aliases\":[\"重复\",\" 重复 \"]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Fish content is invalid"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void mapsPatternSizeNotEmptyAndPositiveRequestViolations() throws Exception {
        mvc.perform(post("/api/v1/admin/fishes")
                        .with(csrf()).contentType(APPLICATION_JSON)
                        .content(validCreateJson("pattern-fish")
                                .replace("\"slug\":\"pattern-fish\"", "\"slug\":\"Bad_slug\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("slug"));
        mvc.perform(post("/api/v1/admin/fishes")
                        .with(csrf()).contentType(APPLICATION_JSON)
                        .content(validCreateJson("size-fish")
                                .replace("\"scientificName\":\"Administratus size-fish\"",
                                        "\"scientificName\":\"" + "x".repeat(161) + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("scientificName"));
        mvc.perform(post("/api/v1/admin/fishes")
                        .with(csrf()).contentType(APPLICATION_JSON)
                        .content(validCreateJson("empty-aliases-fish")
                                .replace("\"aliases\":[\"管理别名\"]", "\"aliases\":[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("aliases"));
        mvc.perform(post("/api/v1/admin/fishes")
                        .with(csrf()).contentType(APPLICATION_JSON)
                        .content(validCreateJson("zero-order-fish")
                                .replace("\"displayOrder\":1", "\"displayOrder\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("displayOrder"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void mapsCatalogConflictsMissingFishAndIllegalTransitions() throws Exception {
        create("conflict-base-fish");
        mvc.perform(post("/api/v1/admin/fishes").with(csrf()).contentType(APPLICATION_JSON)
                        .content(validCreateJson("conflict-base-fish")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_ENTRY_CONFLICT"));
        create("conflict-common-base-fish");
        mvc.perform(post("/api/v1/admin/fishes").with(csrf()).contentType(APPLICATION_JSON)
                        .content(validCreateJson("conflict-common-other-fish")
                                .replace("\"commonNameZh\":\"管理鱼conflict-common-other-fish\"",
                                        "\"commonNameZh\":\"管理鱼conflict-common-base-fish\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_ENTRY_CONFLICT"));
        create("conflict-scientific-base-fish");
        mvc.perform(post("/api/v1/admin/fishes").with(csrf()).contentType(APPLICATION_JSON)
                        .content(validCreateJson("conflict-scientific-other-fish")
                                .replace("\"scientificName\":\"Administratus conflict-scientific-other-fish\"",
                                        "\"scientificName\":\"Administratus conflict-scientific-base-fish\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_ENTRY_CONFLICT"));
        mvc.perform(get("/api/v1/admin/fishes/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FISH_NOT_FOUND"));
        long draftId = create("transition-draft-fish");
        mvc.perform(post("/api/v1/admin/fishes/{id}/unpublish", draftId).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_PUBLICATION_TRANSITION"));
    }

    private long create(String slug) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/admin/fishes")
                        .with(csrf()).contentType(APPLICATION_JSON).content(validCreateJson(slug)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static String validCreateJson(String slug) {
        return """
                {
                  "slug":"%s",
                  "commonNameZh":"管理鱼%s",
                  "scientificName":"Administratus %s",
                  "familyNameZh":"管理科",
                  "familyScientificName":"Administratidae",
                  "genusNameZh":"管理属",
                  "genusScientificName":"Administratus",
                  "aliases":["管理别名"],
                  "habitats":["LAKE"],
                  "appearance":"外观描述",
                  "sizeDescription":"体型描述",
                  "habitatDescription":"栖息环境描述",
                  "distribution":"分布描述",
                  "description":"综合介绍",
                  "imagePath":"/images/fish/%s.jpg",
                  "imageAltText":"管理鱼图片",
                  "imageSourceUrl":"https://example.com/source",
                  "imageAuthor":"测试作者",
                  "imageLicenseName":"CC BY 4.0",
                  "imageLicenseUrl":"https://example.com/license",
                  "displayOrder":1
                }
                """.formatted(slug, slug, slug, slug);
    }

    private static String validUpdateJson(String commonNameZh) {
        return """
                {
                  "commonNameZh":"%s",
                  "scientificName":"Edited fish",
                  "familyNameZh":"编辑科",
                  "familyScientificName":"Editedidae",
                  "genusNameZh":"编辑属",
                  "genusScientificName":"Editedus",
                  "aliases":["编辑别名"],
                  "habitats":["POND"],
                  "appearance":"编辑外观",
                  "sizeDescription":"编辑体型",
                  "habitatDescription":"编辑栖息地",
                  "distribution":"编辑分布",
                  "description":"编辑介绍",
                  "imagePath":"/images/fish/edited-fish.jpg",
                  "imageAltText":"编辑图片",
                  "imageSourceUrl":"https://example.com/edited",
                  "imageAuthor":"编辑作者",
                  "imageLicenseName":"CC BY 4.0",
                  "imageLicenseUrl":"https://example.com/license",
                  "displayOrder":2
                }
                """.formatted(commonNameZh);
    }
}
