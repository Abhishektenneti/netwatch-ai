package com.netwatch.processor.config;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global Jackson tweaks. Naming strategy + property inclusion come from
 * {@code application.yml}; here we just ensure java.time types serialise
 * as ISO-8601 strings rather than epoch millis.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer javaTimeCustomizer() {
        return builder -> builder.modules(new JavaTimeModule());
    }
}
