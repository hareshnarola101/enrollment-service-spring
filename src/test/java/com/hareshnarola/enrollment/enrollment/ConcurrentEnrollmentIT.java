package com.hareshnarola.enrollment.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.hareshnarola.enrollment.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

/**
 * Fires requests at the same course at the same moment and checks that the row lock
 * keeps the seat count exact. Without the lock these tests overbook or create duplicates.
 */
class ConcurrentEnrollmentIT extends IntegrationTest {

    private static final int CAPACITY = 5;
    private static final int STUDENTS = 20;

    @Test
    void neverConfirmsMoreStudentsThanThereAreSeats() throws Exception {
        long courseId = createCourse(CAPACITY);
        List<Student> students = IntStream.range(0, STUDENTS).mapToObj(i -> registerStudent()).toList();

        List<ResponseEntity<JsonNode>> responses = runConcurrently(
                students.stream().<Callable<ResponseEntity<JsonNode>>>map(s -> () -> enroll(courseId, s)).toList());

        assertThat(responses).allMatch(r -> r.getStatusCode().value() == 201);
        Map<String, Long> byStatus = responses.stream()
                .collect(Collectors.groupingBy(r -> r.getBody().get("status").asText(), Collectors.counting()));
        assertThat(byStatus).containsEntry("CONFIRMED", (long) CAPACITY)
                .containsEntry("WAITLISTED", (long) (STUDENTS - CAPACITY));
        assertThat(getCourse(courseId).get("seatsTaken").asInt()).isEqualTo(CAPACITY);
    }

    @Test
    void oneStudentDoubleClickingGetsExactlyOneEnrollment() throws Exception {
        long courseId = createCourse(CAPACITY);
        Student student = registerStudent();

        List<ResponseEntity<JsonNode>> responses = runConcurrently(
                IntStream.range(0, 8).<Callable<ResponseEntity<JsonNode>>>mapToObj(i -> () -> enroll(courseId, student))
                        .toList());

        Map<Integer, Long> byStatusCode = responses.stream()
                .map(ResponseEntity::getStatusCode)
                .map(HttpStatusCode::value)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(byStatusCode).containsEntry(201, 1L).containsEntry(409, 7L);
        assertThat(getCourse(courseId).get("seatsTaken").asInt()).isEqualTo(1);
    }

    @Test
    void concurrentCancellationsHandEachFreedSeatToADifferentStudent() throws Exception {
        long courseId = createCourse(3);
        List<Student> holders = IntStream.range(0, 3).mapToObj(i -> registerStudent()).toList();
        List<Long> holderEnrollments = holders.stream()
                .map(s -> enroll(courseId, s).getBody().get("id").asLong()).toList();
        List<Student> waiting = IntStream.range(0, 4).mapToObj(i -> registerStudent()).toList();
        waiting.forEach(s -> enroll(courseId, s));

        List<Callable<ResponseEntity<JsonNode>>> cancellations = new ArrayList<>();
        for (int i = 0; i < holders.size(); i++) {
            long enrollmentId = holderEnrollments.get(i);
            String token = holders.get(i).token();
            cancellations.add(() -> cancel(enrollmentId, token));
        }
        runConcurrently(cancellations);

        JsonNode confirmed = call(HttpMethod.GET,
                "/api/courses/" + courseId + "/enrollments?status=CONFIRMED", adminToken(), null).getBody();
        assertThat(confirmed.get("totalElements").asLong()).isEqualTo(3);
        assertThat(getCourse(courseId).get("seatsTaken").asInt()).isEqualTo(3);
    }

    private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = tasks.stream()
                    .map(task -> pool.submit(() -> {
                        start.await();
                        return task.call();
                    }))
                    .toList();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
