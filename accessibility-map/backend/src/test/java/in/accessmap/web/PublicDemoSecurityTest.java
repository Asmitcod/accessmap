package in.accessmap.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.public-contributions-enabled=false",
        "spring.datasource.url=jdbc:h2:mem:accessmap-demo-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
class PublicDemoSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowsPublicBrowsingButDisablesPublicContributionsAndModeration() throws Exception {
        mockMvc.perform(get("/api/places"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"demo@example.com","password":"Correct-Horse-Battery-7!"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/places/1/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ENTRANCE","label":"Disabled demo entrance","status":"STEP_FREE"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/moderation/reports"))
                .andExpect(status().isUnauthorized());
    }
}
