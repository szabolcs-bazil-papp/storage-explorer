package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class DemoSecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                 CorsConfigurationSource corsConfigurationSource)
      throws Exception {
    http
        .authorizeHttpRequests(it -> it.anyRequest().permitAll())
        .csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults());
    return http.build();
  }

}
