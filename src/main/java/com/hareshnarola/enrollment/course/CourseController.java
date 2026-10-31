package com.hareshnarola.enrollment.course;

import java.net.URI;

import com.hareshnarola.enrollment.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
@Tag(name = "Courses")
class CourseController {

    private final CourseService courseService;

    CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    @Operation(summary = "List courses", description = "Paginated; supports page, size and sort query parameters")
    PageResponse<CourseResponse> list(@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC)
                                      Pageable pageable) {
        return PageResponse.of(courseService.list(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a course")
    CourseResponse get(@PathVariable Long id) {
        return courseService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a course (admin)")
    ResponseEntity<CourseResponse> create(@Valid @RequestBody CreateCourseRequest request) {
        CourseResponse created = courseService.create(request);
        return ResponseEntity.created(URI.create("/api/courses/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a course (admin)",
            description = "Raising capacity promotes waitlisted students into the new seats")
    CourseResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCourseRequest request) {
        return courseService.update(id, request);
    }
}
