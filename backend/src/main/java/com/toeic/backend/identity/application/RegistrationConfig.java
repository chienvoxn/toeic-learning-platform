package com.toeic.backend.identity.application;

import java.security.SecureRandom;
import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(RegistrationProperties.class)
public class RegistrationConfig {

    @Bean
    public PasswordEncoder registrationPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public Clock registrationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecureRandom registrationRandom() {
        return new SecureRandom();
    }
}