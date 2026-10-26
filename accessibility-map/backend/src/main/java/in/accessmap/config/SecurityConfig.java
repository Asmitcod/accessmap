package in.accessmap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Value("${app.public-contributions-enabled:true}") boolean publicContributionsEnabled) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(requests -> {
                    requests.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/places/**", "/actuator/health").permitAll();
                    if (publicContributionsEnabled) {
                        requests.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/api/places/*/features",
                                        "/api/places/*/features/*/reports")
                                .authenticated()
                                .requestMatchers("/api/moderation/**").hasRole("MODERATOR");
                    } else {
                        requests.requestMatchers(
                                        HttpMethod.POST,
                                        "/api/auth/register",
                                        "/api/auth/login",
                                        "/api/places/*/features",
                                        "/api/places/*/features/*/reports",
                                        "/api/moderation/**")
                                .denyAll();
                    }
                    requests.anyRequest().denyAll();
                });
        return http
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
