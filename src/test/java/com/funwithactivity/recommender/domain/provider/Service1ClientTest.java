package com.funwithactivity.recommender.domain.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funwithactivity.recommender.config.ProviderProperties;
import com.funwithactivity.recommender.generated.service1.model.Service1RecommendationItem;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class Service1ClientTest {

    private MockRestServiceServer mockServer;
    private Service1Client client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://provider.test");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        ProviderProperties properties = new ProviderProperties();
        properties.getProviders().getService1().setBaseUrl("https://provider.test");
        properties.getProviders().getService1().setPath("/services/service1");
        properties.getProviders().getService1().setToken("service1-dev");
        properties.getProviders().getService1().setTimeout(Duration.ofSeconds(2));

        client = new Service1Client(restClient, properties, new ObjectMapper());
    }

    @Test
    void unwrapsLambdaEnvelopeAndParsesBody() {
        String envelope = """
                {"statusCode":200,"body":"[{\\"confidence\\": 0.6, \\"recommendation\\": \\"Walk more\\"}]"}
                """;

        mockServer.expect(requestTo("https://provider.test/services/service1"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.height").value(184.0))
                .andExpect(jsonPath("$.weight").value(84.0))
                .andExpect(jsonPath("$.token").value("service1-dev"))
                .andRespond(withSuccess(envelope, MediaType.APPLICATION_JSON));

        List<Service1RecommendationItem> result = client.fetchRecommendations(184.0, 84.0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getConfidence()).isEqualTo(0.6);
        assertThat(result.get(0).getRecommendation()).isEqualTo("Walk more");
        mockServer.verify();
    }

    @Test
    void wrapsNon2xxResponsesAsProviderException() {
        mockServer.expect(requestTo("https://provider.test/services/service1"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchRecommendations(184.0, 84.0))
                .isInstanceOf(ProviderException.class);
    }

    @Test
    void wrapsUnparseableEnvelopeBodyAsProviderException() {
        String envelope = """
                {"statusCode":200,"body":"not-json"}
                """;

        mockServer.expect(requestTo("https://provider.test/services/service1"))
                .andRespond(withSuccess(envelope, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchRecommendations(184.0, 84.0))
                .isInstanceOf(ProviderException.class);
    }

    @Test
    void wrapsNonSuccessEnvelopeStatusCodeAsProviderExceptionInsteadOfNpe() {
        // Non-2xx envelope status must surface as ProviderException, not reach the array parser.
        String envelope = """
                {"statusCode":501,"body":"{\\"errorCode\\": 97, \\"errorMessage\\": \\"function not working properly yet\\", \\"statusCode\\": 503}"}
                """;

        mockServer.expect(requestTo("https://provider.test/services/service1"))
                .andRespond(withSuccess(envelope, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchRecommendations(184.0, 84.0))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("statusCode=501")
                .hasMessageContaining("function not working properly yet");
    }
}
