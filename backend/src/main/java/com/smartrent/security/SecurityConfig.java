package com.smartrent.security;

import com.smartrent.domain.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Session-based authentication (form login) with role-based authorization enforced on the server.
 * CSRF protection stays ON: the XSRF-TOKEN cookie is readable by the page scripts, which echo it in X-XSRF-TOKEN.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, RestSecurityHandlers handlers) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/vehicles", "/vehicles/*", "/login", "/register", "/error",
                        "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/meta", "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/vehicles", "/api/vehicles/*", "/api/vehicles/*/quote",
                        "/api/vehicles/*/booked-ranges").permitAll()
                .requestMatchers("/admin/**", "/api/admin/**").hasRole(Role.ADMIN.name())
                .requestMatchers("/staff/**", "/api/staff/**")
                    .hasAnyRole(Role.EMPLOYEE.name(), Role.ADMIN.name())
                .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(handlers, new AntPathRequestMatcher("/api/**"))
                .accessDeniedHandler(handlers))
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(roleBasedSuccess())
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST"))
                .logoutSuccessUrl("/")
                .deleteCookies("JSESSIONID"));
        return http.build();
    }

    private AuthenticationSuccessHandler roleBasedSuccess() {
        return (request, response, authentication) -> {
            String target = "/account";
            for (var a : authentication.getAuthorities()) {
                if (a.getAuthority().equals("ROLE_ADMIN")) {
                    target = "/admin";
                    break;
                }
                if (a.getAuthority().equals("ROLE_EMPLOYEE")) {
                    target = "/staff";
                }
            }
            response.sendRedirect(request.getContextPath() + target);
        };
    }
}
