package com.stylemind.stylist.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "stylemind.stylist.knowledge.enabled", havingValue = "true")
public class Neo4jConfiguration {
    @Bean(destroyMethod = "close")
    Driver stylistKnowledgeDriver(
            @Value("${stylemind.stylist.knowledge.neo4j-uri}") String uri,
            @Value("${stylemind.stylist.knowledge.neo4j-username:}") String username,
            @Value("${stylemind.stylist.knowledge.neo4j-password:}") String password) {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Neo4j credentials must be configured through environment variables");
        }
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }
}
