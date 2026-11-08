package com.bank.avpiteam.security;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecurityFilterChain apiChain(HttpSecurity http, ProblemJsonHandlers problems,
                                 @Value("${security.metrics.allowed-cidrs:127.0.0.0/8,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}")
                                 String[] cidrs) throws Exception {

        List<IpAddressMatcher> metricsNets = Arrays.stream(cidrs)
                .map(String::trim).map(IpAddressMatcher::new).toList();

        String view = Permission.VIEW_DATA.name();
        String edit = Permission.EDIT_MAPPING.name();
        String nlp = Permission.MANAGE_NLP.name();
        String users = Permission.MANAGE_USERS.name();
        HttpMethod get = HttpMethod.GET;

        http
                .csrf(c -> c.disable())   // stateless Basic, cookie-сессий нет
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.authenticationEntryPoint(problems))
                .exceptionHandling(e -> e.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(antMatcher("/actuator/health/**")).permitAll()
                        .requestMatchers(antMatcher("/actuator/prometheus")).access((auth, ctx) ->
                                new AuthorizationDecision(
                                        metricsNets.stream().anyMatch(m -> m.matches(ctx.getRequest()))))
                        .requestMatchers(antMatcher("/swagger-ui/**"), antMatcher("/swagger-ui.html"),
                                antMatcher("/v3/api-docs/**")).permitAll()
                        .requestMatchers(antMatcher("/api/pipeline/**"), antMatcher("/api/demo/reset"))
                        .hasRole(Role.ADMIN.name())
                        .requestMatchers(antMatcher(get, "/api/demo/report")).hasAuthority(view)
                        .requestMatchers(antMatcher("/api/me")).authenticated()
                        .requestMatchers(antMatcher("/api/admin/nlp/**")).hasAuthority(nlp)
                        .requestMatchers(antMatcher("/api/admin/users/**")).hasAuthority(users)
                        .requestMatchers(antMatcher(HttpMethod.POST, "/api/operations/*/mapping/resolve"))
                        .hasAuthority(edit)
                        .requestMatchers(antMatcher(get, "/api/operations/**"),
                                antMatcher(get, "/api/review-queue/**"),
                                antMatcher(get, "/api/contracts/**")).hasAuthority(view)
                        .anyRequest().denyAll());
        return http.build();
    }
}