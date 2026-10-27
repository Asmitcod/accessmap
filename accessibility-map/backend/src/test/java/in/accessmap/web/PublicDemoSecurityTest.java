package in.accessmap.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "app.public-auth-enabled=true",
        "app.public-contributions-enabled=false",
        "spring.datasource.url=jdbc:h2:mem:accessmap-demo-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
class PublicDemoSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void allowsPublicBrowsingAndAuthenticationButDisablesContributionsAndModeration() throws Exception {
        mockMvc.perform(get("/api/places"))
                .andExpect(status().isOk());

        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"demo@example.com","password":"Correct-Horse-Battery-7!"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(registration.getResponse().getContentAsString())
                .path("token")
                .asText();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"demo@example.com","password":"Correct-Horse-Battery-7!"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/places/1/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ENTRANCE","label":"Disabled demo entrance","status":"STEP_FREE"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/places/1/features")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ENTRANCE","label":"Disabled demo entrance","status":"STEP_FREE"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/moderation/reports"))
                .andExpect(status().isUnauthorized());
    }
}
