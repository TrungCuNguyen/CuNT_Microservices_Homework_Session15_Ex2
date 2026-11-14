package com.example.courseservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CourseController.class)
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/courses should return course list and reflect X-User-Role header")
    void getAllCourses_WithRoleHeader() throws Exception {
        mockMvc.perform(get("/api/courses")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleReceivedInCourseService").value("ADMIN"))
                .andExpect(jsonPath("$.courses").isArray())
                .andExpect(jsonPath("$.total").value(3));
    }
}

