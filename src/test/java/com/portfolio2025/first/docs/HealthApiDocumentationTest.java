package com.portfolio2025.first.docs;

import com.portfolio2025.first.refactor.phase_A.system.HealthController;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("restdocs")
@WebMvcTest(controllers = HealthController.class)
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
class HealthApiDocumentationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void documentHealthApi() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andDo(document("health")); // build/generated-snippets/health/**
    }
}
