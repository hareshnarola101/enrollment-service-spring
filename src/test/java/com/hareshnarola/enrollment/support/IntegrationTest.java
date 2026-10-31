package com.hareshnarola.enrollment.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for tests that run the whole application over HTTP against real containers.
 * Tests create their own users and courses with unique names instead of cleaning tables,
 * so they stay independent while sharing one database and one Spring context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class IntegrationTest {

    protected static final String ADMIN_EMAIL = "admin@test.local";
    protected static final String ADMIN_PASSWORD = "admin-password-123";
    protected static final String STUDENT_PASSWORD = "student-password";

    private static final AtomicInteger COURSE_SEQUENCE = new AtomicInteger();
    private static volatile String cachedAdminToken;

    @Autowired
    protected TestRestTemplate rest;

    protected record Student(long id, String email, String token) {
    }

    protected String adminToken() {
        if (cachedAdminToken == null) {
            cachedAdminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        }
        return cachedAdminToken;
    }

    protected String login(String email, String password) {
        ResponseEntity<JsonNode> response = call(HttpMethod.POST, "/api/auth/login", null,
                Map.of("email", email, "password", password));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().get("accessToken").asText();
    }

    protected Student registerStudent() {
        String email = "student-" + UUID.randomUUID() + "@test.local";
        ResponseEntity<JsonNode> response = call(HttpMethod.POST, "/api/auth/register", null,
                Map.of("email", email, "password", STUDENT_PASSWORD, "fullName", "Test Student"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long id = response.getBody().get("id").asLong();
        return new Student(id, email, login(email, STUDENT_PASSWORD));
    }

    protected long createCourse(int capacity) {
        String code = "C" + COURSE_SEQUENCE.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<JsonNode> response = call(HttpMethod.POST, "/api/courses", adminToken(),
                Map.of("code", code, "title", "Course " + code, "capacity", capacity));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asLong();
    }

    protected ResponseEntity<JsonNode> enroll(long courseId, Student student) {
        return call(HttpMethod.POST, "/api/courses/" + courseId + "/enrollments", student.token(), null);
    }

    protected ResponseEntity<JsonNode> cancel(long enrollmentId, String token) {
        return call(HttpMethod.POST, "/api/enrollments/" + enrollmentId + "/cancel", token, null);
    }

    protected JsonNode getCourse(long courseId) {
        ResponseEntity<JsonNode> response = call(HttpMethod.GET, "/api/courses/" + courseId, adminToken(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    protected String enrollmentStatus(long enrollmentId, String token) {
        ResponseEntity<JsonNode> response = call(HttpMethod.GET, "/api/enrollments/" + enrollmentId, token, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().get("status").asText();
    }

    protected ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    protected static void assertProblem(ResponseEntity<JsonNode> response, HttpStatus status) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().get("status").asInt()).isEqualTo(status.value());
        assertThat(response.getBody().hasNonNull("title")).isTrue();
    }
}
