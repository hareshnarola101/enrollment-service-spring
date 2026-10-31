package com.hareshnarola.enrollment.course;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCourseRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{2,20}", message = "must be 2-20 letters, digits or dashes")
        String code,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @NotNull @Min(1) @Max(10_000) Integer capacity) {
}
