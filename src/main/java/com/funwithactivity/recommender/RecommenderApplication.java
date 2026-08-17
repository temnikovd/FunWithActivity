package com.funwithactivity.recommender;

import com.funwithactivity.recommender.config.ProviderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ProviderProperties.class)
public class RecommenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecommenderApplication.class, args);
    }
}
