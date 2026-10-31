package com.hareshnarola.enrollment.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.hareshnarola.enrollment.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class EnrollmentApiIT extends IntegrationTest {

    @Test
    void confirmsUntilFullThenWaitlists() {
        long courseId = createCourse(1);

        ResponseEntity<JsonNode> first = enroll(courseId, registerStudent());
        ResponseEntity<JsonNode> second = enroll(courseId, registerStudent());

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().get("status").asText()).isEqualTo("WAITLISTED");
        assertThat(getCourse(courseId).get("seatsTaken").asInt()).isEqualTo(1);
    }

    @Test
    void enrollingTwiceIsAConflict() {
        long courseId = createCourse(5);
        Student student = registerStudent();
        enroll(courseId, student);

        assertProblem(enroll(courseId, student), HttpStatus.CONFLICT);
    }

    @Test
    void cancellingAConfirmedSeatPromotesTheFirstStudentInLine() {
        long courseId = createCourse(1);
        Student holder = registerStudent();
        Student firstInLine = registerStudent();
        Student secondInLine = registerStudent();
        long holderEnrollment = enroll(courseId, holder).getBody().get("id").asLong();
        long firstWaiting = enroll(courseId, firstInLine).getBody().get("id").asLong();
        long secondWaiting = enroll(courseId, secondInLine).getBody().get("id").asLong();

        ResponseEntity<JsonNode> response = cancel(holderEnrollment, holder.token());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status").asText()).isEqualTo("CANCELLED");
        assertThat(enrollmentStatus(firstWaiting, firstInLine.token())).isEqualTo("CONFIRMED");
        assertThat(enrollmentStatus(secondWaiting, secondInLine.token())).isEqualTo("WAITLISTED");
        assertThat(getCourse(courseId).get("seatsTaken").asInt()).isEqualTo(1);
    }

    @Test
    void studentsCannotCancelOrViewOtherStudentsEnrollments() {
        long courseId = createCourse(5);
        long enrollmentId = enroll(courseId, registerStudent()).getBody().get("id").asLong();
        Student intruder = registerStudent();

        assertProblem(cancel(enrollmentId, intruder.token()), HttpStatus.FORBIDDEN);
        assertProblem(call(HttpMethod.GET, "/api/enrollments/" + enrollmentId, intruder.token(), null),
                HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanCancelAnyEnrollment() {
        long courseId = createCourse(5);
        long enrollmentId = enroll(courseId, registerStudent()).getBody().get("id").asLong();

        ResponseEntity<JsonNode> response = cancel(enrollmentId, adminToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getCourse(courseId).get("seatsTaken").asInt()).isZero();
    }

    @Test
    void cancellingTwiceIsAConflict() {
        long courseId = createCourse(5);
        Student student = registerStudent();
        long enrollmentId = enroll(courseId, student).getBody().get("id").asLong();
        cancel(enrollmentId, student.token());

        assertProblem(cancel(enrollmentId, student.token()), HttpStatus.CONFLICT);
    }

    @Test
    void studentCanEnrollAgainAfterCancelling() {
        long courseId = createCourse(5);
        Student student = registerStudent();
        long enrollmentId = enroll(courseId, student).getBody().get("id").asLong();
        cancel(enrollmentId, student.token());

        ResponseEntity<JsonNode> again = enroll(courseId, student);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(again.getBody().get("status").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void adminsCannotEnrollThemselves() {
        long courseId = createCourse(5);

        ResponseEntity<JsonNode> response =
                call(HttpMethod.POST, "/api/courses/" + courseId + "/enrollments", adminToken(), null);

        assertProblem(response, HttpStatus.FORBIDDEN);
    }

    @Test
    void enrollingInAnUnknownCourseIs404() {
        assertProblem(enroll(987654L, registerStudent()), HttpStatus.NOT_FOUND);
    }

    @Test
    void adminCanFilterACoursesEnrollmentsByStatus() {
        long courseId = createCourse(1);
        enroll(courseId, registerStudent());
        enroll(courseId, registerStudent());
        enroll(courseId, registerStudent());

        ResponseEntity<JsonNode> response = call(HttpMethod.GET,
                "/api/courses/" + courseId + "/enrollments?status=WAITLISTED", adminToken(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("totalElements").asLong()).isEqualTo(2);
        assertThat(response.getBody().get("content").findValuesAsText("status")).containsOnly("WAITLISTED");
    }

    @Test
    void studentSeesOnlyTheirOwnEnrollments() {
        long courseA = createCourse(5);
        long courseB = createCourse(5);
        Student student = registerStudent();
        enroll(courseA, student);
        enroll(courseB, student);
        enroll(courseA, registerStudent());

        ResponseEntity<JsonNode> response = call(HttpMethod.GET, "/api/enrollments/me", student.token(), null);

        assertThat(response.getBody().get("totalElements").asLong()).isEqualTo(2);
        assertThat(response.getBody().get("content").findValuesAsText("studentId"))
                .containsOnly(String.valueOf(student.id()));
    }
}
