package com.funwithactivity.recommender.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** One {@link RestClient} per provider, each with its own base URL and timeout. */
@Configuration
public class ProviderRestClientConfig {

    @Bean
    public RestClient service1RestClient(ProviderProperties properties) {
        return buildClient(properties.getProviders().getService1());
    }

    @Bean
    public RestClient service2RestClient(ProviderProperties properties) {
        return buildClient(properties.getProviders().getService2());
    }

    private RestClient buildClient(ProviderProperties.Provider provider) {
        Duration timeout = provider.getTimeout();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) timeout.toMillis());
        requestFactory.setReadTimeout((int) timeout.toMillis());

        return RestClient.builder()
                .baseUrl(provider.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
