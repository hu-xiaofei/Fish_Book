package com.fishbook;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fishbook.catalog.domain.FishRepository;
import com.fishbook.favorites.domain.FavoriteRepository;
import com.fishbook.identity.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
})
@AutoConfigureMockMvc
class HealthEndpointTest {
    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    FishRepository fishRepository;

    @MockitoBean
    FavoriteRepository favoriteRepository;

    @Autowired
    MockMvc mvc;

    @Test
    void exposesHealthWithoutAuthentication() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void deniesInfoWithoutAuthentication() throws Exception {
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isForbidden());
    }
}
