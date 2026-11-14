package com.example.courseservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    public static class Course {
        private Long id;
        private String title;
        private String instructor;
        private int durationHours;
        private String userRole;

        public Course() {
        }

        public Course(Long id, String title, String instructor, int durationHours, String userRole) {
            this.id = id;
            this.title = title;
            this.instructor = instructor;
            this.durationHours = durationHours;
            this.userRole = userRole;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getInstructor() {
            return instructor;
        }

        public void setInstructor(String instructor) {
            this.instructor = instructor;
        }

        public int getDurationHours() {
            return durationHours;
        }

        public void setDurationHours(int durationHours) {
            this.durationHours = durationHours;
        }

        public String getUserRole() {
            return userRole;
        }

        public void setUserRole(String userRole) {
            this.userRole = userRole;
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllCourses(
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        List<Course> courses = Arrays.asList(
                new Course(1L, "Spring Boot & Microservices Masterclass", "Rikkei Academy", 40, userRole),
                new Course(2L, "Spring Cloud Gateway & JWT Security", "Rikkei Academy", 30, userRole),
                new Course(3L, "PostgreSQL & Distributed Systems", "Rikkei Academy", 25, userRole)
        );

        Map<String, Object> response = new HashMap<>();
        response.put("roleReceivedInCourseService", userRole);
        response.put("total", courses.size());
        response.put("courses", courses);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Course> getCourseById(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        Course course = new Course(id, "Spring Cloud Gateway & JWT Security", "Rikkei Academy", 30, userRole);
        return ResponseEntity.ok(course);
    }
}

