package com.hareshnarola.enrollment.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.hareshnarola.enrollment.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CourseApiIT extends IntegrationTest {

    @Test
    void adminCreatesACourse() {
        ResponseEntity<JsonNode> response = call(HttpMethod.POST, "/api/courses", adminToken(),
                Map.of("code", "spring-201", "title", "Spring in Practice", "description", "Boot, Data, Security",
                        "capacity", 25));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = response.getBody();
        assertThat(body.get("code").asText()).isEqualTo("SPRING-201");
        assertThat(body.get("seatsTaken").asInt()).isZero();
        assertThat(body.get("seatsAvailable").asInt()).isEqualTo(25);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("/api/courses/" + body.get("id").asLong());
    }

    @Test
    void invalidCourseIsRejectedWithFieldErrors() {
        ResponseEntity<JsonNode> response = call(HttpMethod.POST, "/api/courses", adminToken(),
                Map.of("code", "bad code!", "title", "", "capacity", 0));

        assertProblem(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("errors").findValuesAsText("field"))
                .containsExactlyInAnyOrder("code", "title", "capacity");
    }

    @Test
    void duplicateCodeIsAConflict() {
        Map<String, Object> course = Map.of("code", "DUP-1", "title", "First", "capacity", 5);
        call(HttpMethod.POST, "/api/courses", adminToken(), course);

        assertProblem(call(HttpMethod.POST, "/api/courses", adminToken(), course), HttpStatus.CONFLICT);
    }

    @Test
    void unknownCourseIs404() {
        ResponseEntity<JsonNode> response = call(HttpMethod.GET, "/api/courses/999999", adminToken(), null);

        assertProblem(response, HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("instance").asText()).isEqualTo("/api/courses/999999");
    }

    @Test
    void listIsPaginated() {
        createCourse(5);
        createCourse(5);
        createCourse(5);
        String token = registerStudent().token();

        ResponseEntity<JsonNode> response = call(HttpMethod.GET, "/api/courses?page=0&size=2&sort=id,desc",
                token, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode page = response.getBody();
        assertThat(page.get("content")).hasSize(2);
        assertThat(page.get("size").asInt()).isEqualTo(2);
        assertThat(page.get("totalElements").asLong()).isGreaterThanOrEqualTo(3);
        long first = page.get("content").get(0).get("id").asLong();
        long second = page.get("content").get(1).get("id").asLong();
        assertThat(first).isGreaterThan(second);
    }

    @Test
    void unknownSortPropertyIs400() {
        assertProblem(call(HttpMethod.GET, "/api/courses?sort=passwordHash", adminToken(), null),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void raisingCapacityPromotesWaitlistedStudents() {
        long courseId = createCourse(1);
        Student first = registerStudent();
        Student second = registerStudent();
        enroll(courseId, first);
        long waitlistedId = enroll(courseId, second).getBody().get("id").asLong();

        ResponseEntity<JsonNode> response = call(HttpMethod.PUT, "/api/courses/" + courseId, adminToken(),
                Map.of("title", "Bigger room", "capacity", 2));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("seatsTaken").asInt()).isEqualTo(2);
        assertThat(enrollmentStatus(waitlistedId, second.token())).isEqualTo("CONFIRMED");
    }

    @Test
    void capacityCannotDropBelowConfirmedSeats() {
        long courseId = createCourse(2);
        enroll(courseId, registerStudent());
        enroll(courseId, registerStudent());

        ResponseEntity<JsonNode> response = call(HttpMethod.PUT, "/api/courses/" + courseId, adminToken(),
                Map.of("title", "Smaller room", "capacity", 1));

        assertProblem(response, HttpStatus.CONFLICT);
        assertThat(getCourse(courseId).get("capacity").asInt()).isEqualTo(2);
    }
}
