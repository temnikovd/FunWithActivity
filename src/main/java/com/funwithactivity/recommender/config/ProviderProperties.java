package com.funwithactivity.recommender.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recommender")
public class ProviderProperties {

    private Providers providers = new Providers();
    private Aggregation aggregation = new Aggregation();

    public Providers getProviders() {
        return providers;
    }

    public void setProviders(Providers providers) {
        this.providers = providers;
    }

    public Aggregation getAggregation() {
        return aggregation;
    }

    public void setAggregation(Aggregation aggregation) {
        this.aggregation = aggregation;
    }

    public static class Providers {
        private Provider service1 = new Provider();
        private Provider service2 = new Provider();

        public Provider getService1() {
            return service1;
        }

        public void setService1(Provider service1) {
            this.service1 = service1;
        }

        public Provider getService2() {
            return service2;
        }

        public void setService2(Provider service2) {
            this.service2 = service2;
        }
    }

    public static class Provider {
        private String baseUrl;
        private String path;
        private String token;
        private Duration timeout = Duration.ofSeconds(2);

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    public static class Aggregation {
        private Duration overallTimeout = Duration.ofSeconds(3);

        public Duration getOverallTimeout() {
            return overallTimeout;
        }

        public void setOverallTimeout(Duration overallTimeout) {
            this.overallTimeout = overallTimeout;
        }
    }
}
