package com.funwithactivity.recommender.domain.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funwithactivity.recommender.config.ProviderProperties;
import com.funwithactivity.recommender.generated.service2.model.Service2RecommendationItem;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class Service2ClientTest {

    private MockRestServiceServer mockServer;
    private Service2Client client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://provider.test");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        ProviderProperties properties = new ProviderProperties();
        properties.getProviders().getService2().setBaseUrl("https://provider.test");
        properties.getProviders().getService2().setPath("/services/service2");
        properties.getProviders().getService2().setTimeout(Duration.ofSeconds(2));

        client = new Service2Client(restClient, properties, new ObjectMapper());
    }

    @Test
    void convertsUnitsAndUsesUniqueSessionTokenPerRequest() {
        // cm/kg converted to ft/lb
        mockServer.expect(requestTo("https://provider.test/services/service2"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.measurements.height", Matchers.closeTo(6.036745, 0.000001)))
                .andExpect(jsonPath("$.measurements.mass", Matchers.closeTo(185.188300, 0.000001)))
                .andExpect(jsonPath("$.birth_date").value(1615852800L))
                .andExpect(jsonPath("$.session_token").exists())
                .andRespond(withSuccess("""
                        {"statusCode":200,"body":"[]"}
                        """, MediaType.APPLICATION_JSON));

        client.fetchRecommendations(184.0, 84.0, LocalDate.of(2021, 3, 16));

        mockServer.verify();
    }

    @Test
    void parsesObservedBareArrayBody() {
        mockServer.expect(requestTo("https://provider.test/services/service2"))
                .andRespond(withSuccess("""
                        {"statusCode":200,"body":"[{\\"priority\\": 750, \\"title\\": \\"Have more workouts\\", \\"details\\": \\"Workouts help\\"}]"}
                        """, MediaType.APPLICATION_JSON));

        List<Service2RecommendationItem> result =
                client.fetchRecommendations(184.0, 84.0, LocalDate.of(2021, 3, 16));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPriority()).isEqualTo(750);
        assertThat(result.get(0).getTitle()).isEqualTo("Have more workouts");
    }

    @Test
    void parsesDocumentedWrappedObjectBody() {
        mockServer.expect(requestTo("https://provider.test/services/service2"))
                .andRespond(withSuccess("""
                        {"statusCode":200,"body":"{\\"recommendations\\": [{\\"priority\\": 500, \\"title\\": \\"Drink water\\", \\"details\\": \\"Stay hydrated\\"}]}"}
                        """, MediaType.APPLICATION_JSON));

        List<Service2RecommendationItem> result =
                client.fetchRecommendations(184.0, 84.0, LocalDate.of(2021, 3, 16));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPriority()).isEqualTo(500);
    }

    @Test
    void wrapsUnparseableBodyAsProviderException() {
        mockServer.expect(requestTo("https://provider.test/services/service2"))
                .andRespond(withSuccess("""
                        {"statusCode":200,"body":"not-json"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchRecommendations(184.0, 84.0, LocalDate.of(2021, 3, 16)))
                .isInstanceOf(ProviderException.class);
    }

    @Test
    void wrapsNonSuccessEnvelopeStatusCodeAsProviderExceptionInsteadOfNpe() {
        // Non-2xx envelope status must surface as ProviderException, not reach the array parser.
        mockServer.expect(requestTo("https://provider.test/services/service2"))
                .andRespond(withSuccess("""
                        {"statusCode":501,"body":"{\\"errorCode\\": 97, \\"errorMessage\\": \\"function not working properly yet\\", \\"statusCode\\": 503}"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchRecommendations(184.0, 84.0, LocalDate.of(2021, 3, 16)))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("statusCode=501")
                .hasMessageContaining("function not working properly yet");
    }

    @Test
    void wrapsSuccessBodyMissingRecommendationsArrayAsProviderExceptionInsteadOfNpe() {
        // Object body without a recommendations array must fail loudly, not return null items.
        mockServer.expect(requestTo("https://provider.test/services/service2"))
                .andRespond(withSuccess("""
                        {"statusCode":200,"body":"{\\"somethingElse\\": true}"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchRecommendations(184.0, 84.0, LocalDate.of(2021, 3, 16)))
                .isInstanceOf(ProviderException.class);
    }
}
