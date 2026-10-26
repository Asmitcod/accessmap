package in.accessmap.web;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.IntStream;

import com.jayway.jsonpath.JsonPath;
import in.accessmap.domain.AppUser;
import in.accessmap.domain.Feature;
import in.accessmap.domain.FeatureType;
import in.accessmap.domain.Report;
import in.accessmap.domain.ReportStatus;
import in.accessmap.domain.ReviewDecision;
import in.accessmap.domain.ReviewStatus;
import in.accessmap.repository.AppUserRepository;
import in.accessmap.repository.FeatureRepository;
import in.accessmap.repository.PlaceRepository;
import in.accessmap.repository.ReportRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:accessmap-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
@AutoConfigureMockMvc
class PlaceControllerTest {
    private static final String TEST_PASSWORD = "Correct-Horse-Battery-7!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void listsImportedPlacesWithoutInventingAccessibilityFeatures() throws Exception {
        mockMvc.perform(get("/api/places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(245)));

        mockMvc.perform(get("/api/places").param("search", "British Council Division"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].demoData", is(false)))
                .andExpect(jsonPath("$[0].name", containsString("British Council")))
                .andExpect(jsonPath("$[0].features", hasSize(0)));

        mockMvc.perform(get("/api/places").param("search", "Hospital"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].name", containsString("Hospital")));
    }

    @Test
    void createsLiftFeatureWithInitialReportAndAllowsFurtherUpdates() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/places").param("search", "Central Hospital"))
                .andExpect(status().isOk())
                .andReturn();
        String response = result.getResponse().getContentAsString();
        String placeId = JsonPath.read(response, "$[0].id").toString();
        String placeName = JsonPath.read(response, "$[0].name");

        mockMvc.perform(post("/api/places/{placeId}/features", placeId)
                        .header("Authorization", "Bearer " + registerAndGetToken())
                        .contentType("application/json")
                        .content("""
                                {"type":"LIFT","label":"Main building lift","status":"WORKING","note":"Initial lift observation."}
                                """))
                .andExpect(status().isCreated());

        String featureId = featureRepository.findAll().stream()
                .filter(feature -> feature.getLabel().equals("Main building lift"))
                .map(feature -> feature.getId().toString())
                .findFirst()
                .orElseThrow();
        String moderatorToken = createModeratorAndGetToken();
        long initialReportId = reportIdWithNote("Initial lift observation.");
        mockMvc.perform(post("/api/moderation/reports/{reportId}/decision", initialReportId)
                        .header("Authorization", "Bearer " + moderatorToken)
                        .contentType("application/json")
                        .content("""
                                {"decision":"APPROVE"}
                                """))
                .andExpect(status().isNoContent());

        MvcResult afterCreate = mockMvc.perform(get("/api/places").param("search", placeName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].features[0].confirmationCount", is(1)))
                .andReturn();
        featureId = JsonPath.read(afterCreate.getResponse().getContentAsString(), "$[0].features[0].id");

        mockMvc.perform(post("/api/places/{placeId}/features/{featureId}/reports", placeId, featureId)
                        .header("Authorization", "Bearer " + registerAndGetToken())
                        .contentType("application/json")
                        .content("""
                                {"status":"OUT_OF_SERVICE","note":"Second lift observation."}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/moderation/reports/{reportId}/decision", reportIdWithNote("Second lift observation."))
                        .header("Authorization", "Bearer " + moderatorToken)
                        .contentType("application/json")
                        .content("""
                                {"decision":"APPROVE"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/places").param("search", placeName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].features[0].status", is("OUT_OF_SERVICE")))
                .andExpect(jsonPath("$[0].features[0].confirmationCount", is(2)));
    }

    @Test
    void keepsReportsHiddenUntilApprovalAndAllowsModeratorsToRejectThem() throws Exception {
        MvcResult places = mockMvc.perform(get("/api/places"))
                .andExpect(status().isOk())
                .andReturn();
        String placeId = JsonPath.read(places.getResponse().getContentAsString(), "$[0].id").toString();
        String reporterToken = registerAndGetToken();

        mockMvc.perform(get("/api/moderation/reports")
                        .header("Authorization", "Bearer " + reporterToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/moderation/reports/audit")
                        .header("Authorization", "Bearer " + reporterToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/places/{placeId}/features", placeId)
                        .header("Authorization", "Bearer " + reporterToken)
                        .contentType("application/json")
                        .content("""
                                {"type":"RAMP","label":"Moderation approved ramp","status":"GENTLE","note":"Approval test."}
                                """))
                .andExpect(status().isCreated());

        long reportId = reportIdWithNote("Approval test.");
        mockMvc.perform(get("/api/moderation/reports")
                        .header("Authorization", "Bearer " + reporterToken))
                .andExpect(status().isForbidden());

        String moderatorToken = createModeratorAndGetToken();
        mockMvc.perform(get("/api/moderation/reports")
                        .header("Authorization", "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + reportId + ")]", hasSize(1)));

        mockMvc.perform(post("/api/moderation/reports/{reportId}/decision", reportId)
                        .header("Authorization", "Bearer " + moderatorToken)
                        .contentType("application/json")
                        .content("""
                                {"decision":"APPROVE"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/moderation/reports/audit")
                        .header("Authorization", "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.reportId == " + reportId + " && @.decision == 'APPROVED')]", hasSize(1)));

        mockMvc.perform(post("/api/moderation/reports/{reportId}/decision", reportId)
                        .header("Authorization", "Bearer " + moderatorToken)
                        .contentType("application/json")
                        .content("""
                                {"decision":"REJECT"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].features[?(@.label == 'Moderation approved ramp')]", hasSize(1)));

        mockMvc.perform(post("/api/places/{placeId}/features", placeId)
                        .header("Authorization", "Bearer " + reporterToken)
                        .contentType("application/json")
                        .content("""
                                {"type":"RESTROOM","label":"Moderation rejected restroom","status":"USABLE","note":"Rejection test."}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/moderation/reports/{reportId}/decision", reportIdWithNote("Rejection test."))
                        .header("Authorization", "Bearer " + moderatorToken)
                        .contentType("application/json")
                        .content("""
                                {"decision":"REJECT"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/moderation/reports/audit")
                        .header("Authorization", "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.decision == 'REJECTED')]", hasSize(greaterThan(0))));

        mockMvc.perform(get("/api/places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].features[?(@.label == 'Moderation rejected restroom')]", hasSize(0)));

        mockMvc.perform(post("/api/places/{placeId}/features", placeId)
                        .header("Authorization", "Bearer " + registerAndGetToken())
                        .contentType("application/json")
                        .content("""
                                {"type":"RESTROOM","label":"Moderation rejected restroom","status":"USABLE","note":"Resubmission after rejection."}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsStatusThatDoesNotMatchNewFeatureType() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/places"))
                .andExpect(status().isOk())
                .andReturn();
        String placeId = JsonPath.read(result.getResponse().getContentAsString(), "$[0].id").toString();

        mockMvc.perform(post("/api/places/{placeId}/features", placeId)
                        .header("Authorization", "Bearer " + registerAndGetToken())
                        .contentType("application/json")
                        .content("""
                                {"type":"LIFT","label":"Invalid lift","status":"STEP_FREE"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preventsDuplicateFeatureReportsAtSamePlace() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/places"))
                .andExpect(status().isOk())
                .andReturn();
        String placeId = JsonPath.read(result.getResponse().getContentAsString(), "$[0].id").toString();
        String request = """
                {"type":"RAMP","label":"North side ramp","status":"GENTLE"}
                """;

        mockMvc.perform(post("/api/places/{placeId}/features", placeId)
                        .header("Authorization", "Bearer " + registerAndGetToken())
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/places/{placeId}/features", placeId)
                        .header("Authorization", "Bearer " + registerAndGetToken())
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isConflict());
    }

    @Test
    void preventsAnAccountFromReportingTheSameFeatureAgainWithin24Hours() throws Exception {
        String token = registerAndGetToken();
        MvcResult places = mockMvc.perform(get("/api/places"))
                .andExpect(status().isOk())
                .andReturn();
        String placesJson = places.getResponse().getContentAsString();
        String placeId = JsonPath.read(placesJson, "$[0].id").toString();

        mockMvc.perform(post("/api/places/{placeId}/features", placeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"type":"LIFT","label":"Duplicate guard lift","status":"WORKING"}
                                """))
                .andExpect(status().isCreated());

        String featureId = featureRepository.findAll().stream()
                .filter(feature -> feature.getLabel().equals("Duplicate guard lift"))
                .map(feature -> feature.getId().toString())
                .findFirst()
                .orElseThrow();
        mockMvc.perform(post("/api/places/{placeId}/features/{featureId}/reports", placeId, featureId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"status":"INTERMITTENT"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void limitsAnAccountToTenReportsPerRollingHour() throws Exception {
        String token = registerAndGetToken();
        MvcResult result = mockMvc.perform(get("/api/places"))
                .andExpect(status().isOk())
                .andReturn();
        String placeId = JsonPath.read(result.getResponse().getContentAsString(), "$[0].id").toString();

        for (int reportNumber = 1; reportNumber <= 10; reportNumber++) {
            mockMvc.perform(post("/api/places/{placeId}/features", placeId)
                            .header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .content("""
                                    {"type":"ENTRANCE","label":"Hourly entrance %d","status":"STEP_FREE"}
                                    """.formatted(reportNumber)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/places/{placeId}/features", placeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"type":"ENTRANCE","label":"Hourly entrance 11","status":"STEP_FREE"}
                                """))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void limitsAnAccountToFiftyReportsPerRollingDay() throws Exception {
        String email = "daily-limit-" + UUID.randomUUID() + "@example.com";
        String token = registerAndGetToken(email);
        MvcResult places = mockMvc.perform(get("/api/places"))
                .andExpect(status().isOk())
                .andReturn();
        String placeId = JsonPath.read(places.getResponse().getContentAsString(), "$[0].id").toString();
        AppUser user = userRepository.findByEmail(email).orElseThrow();
        Feature feature = featureRepository.save(new Feature(
                placeRepository.findById(Long.parseLong(placeId)).orElseThrow(),
                FeatureType.LIFT,
                "Daily limit seed lift"));
        Instant reportedAt = Instant.now().minus(2, ChronoUnit.HOURS);
        reportRepository.saveAll(IntStream.range(0, 50)
                .mapToObj(index -> new Report(feature, user, ReportStatus.WORKING, null, reportedAt, 14))
                .toList());

        mockMvc.perform(post("/api/places/{placeId}/features", placeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"type":"ENTRANCE","label":"Daily limit entrance","status":"STEP_FREE"}
                                """))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void requiresAuthenticationToSubmitReport() throws Exception {
        mockMvc.perform(post("/api/places/1/features")
                        .contentType("application/json")
                        .content("""
                                {"type":"ENTRANCE","label":"Front door","status":"STEP_FREE"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationNormalizesEmailAndLoginIssuesAccessToken() throws Exception {
        String email = "mapuser-" + UUID.randomUUID() + "@example.com";
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email.toUpperCase(), TEST_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is(email)))
                .andReturn();
        String token = JsonPath.read(registration.getResponse().getContentAsString(), "$.token");

        mockMvc.perform(post("/api/places/1/features")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"type":"ENTRANCE","label":"Authenticated entrance","status":"STEP_FREE"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(email)))
                .andExpect(jsonPath("$.token", containsString(".")));
    }

    @Test
    void rejectsMalformedBearerToken() throws Exception {
        mockMvc.perform(post("/api/places/1/features")
                        .header("Authorization", "Bearer not-a-jwt")
                        .contentType("application/json")
                        .content("""
                                {"type":"ENTRANCE","label":"Front door","status":"STEP_FREE"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private String registerAndGetToken() throws Exception {
        return registerAndGetToken("reporter-" + UUID.randomUUID() + "@example.com");
    }

    private String registerAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, TEST_PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private String createModeratorAndGetToken() throws Exception {
        String email = "moderator-" + UUID.randomUUID() + "@example.com";
        userRepository.save(new AppUser(
                email,
                passwordEncoder.encode(TEST_PASSWORD),
                Instant.now(),
                true));
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderator", is(true)))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private long reportIdWithNote(String note) {
        return reportRepository.findAll().stream()
                .filter(report -> note.equals(report.getNote()))
                .filter(report -> report.getReviewStatus() == ReviewStatus.PENDING)
                .map(Report::getId)
                .findFirst()
                .orElseThrow();
    }
}
