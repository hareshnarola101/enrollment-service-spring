package com.hareshnarola.enrollment.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.hareshnarola.enrollment.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuthApiIT extends IntegrationTest {

    @Test
    void registeredStudentCanLogInAndCallTheApi() {
        Student student = registerStudent();

        ResponseEntity<JsonNode> response = call(HttpMethod.GET, "/api/enrollments/me", student.token(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("totalElements").asLong()).isZero();
    }

    @Test
    void wrongPasswordIsRejectedWithAProblemDetail() {
        Student student = registerStudent();

        ResponseEntity<JsonNode> response = call(HttpMethod.POST, "/api/auth/login", null,
                Map.of("email", student.email(), "password", "not-the-password"));

        assertProblem(response, HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("detail").asText()).isEqualTo("Invalid email or password");
    }

    @Test
    void requestsWithoutATokenGet401() {
        ResponseEntity<JsonNode> response = call(HttpMethod.GET, "/api/courses", null, null);

        assertProblem(response, HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    }

    @Test
    void tamperedTokensGet401() {
        String token = registerStudent().token();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertProblem(call(HttpMethod.GET, "/api/courses", tampered, null), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void studentsCannotCreateCourses() {
        Student student = registerStudent();

        ResponseEntity<JsonNode> response = call(HttpMethod.POST, "/api/courses", student.token(),
                Map.of("code", "HACK-1", "title", "Nope", "capacity", 5));

        assertProblem(response, HttpStatus.FORBIDDEN);
    }

    @Test
    void registrationIsValidated() {
        ResponseEntity<JsonNode> response = call(HttpMethod.POST, "/api/auth/register", null,
                Map.of("email", "not-an-email", "password", "short", "fullName", ""));

        assertProblem(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("errors").findValuesAsText("field"))
                .containsExactlyInAnyOrder("email", "password", "fullName");
    }

    @Test
    void emailCanOnlyBeRegisteredOnce() {
        Student student = registerStudent();

        ResponseEntity<JsonNode> response = call(HttpMethod.POST, "/api/auth/register", null,
                Map.of("email", student.email().toUpperCase(), "password", STUDENT_PASSWORD, "fullName", "Again"));

        assertProblem(response, HttpStatus.CONFLICT);
    }

    @Test
    void healthAndApiDocsArePublic() {
        ResponseEntity<JsonNode> health = call(HttpMethod.GET, "/actuator/health", null, null);
        ResponseEntity<JsonNode> docs = call(HttpMethod.GET, "/v3/api-docs", null, null);

        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody().get("status").asText()).isEqualTo("UP");
        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(docs.getBody().get("paths").has("/api/courses/{courseId}/enrollments")).isTrue();
    }
}
