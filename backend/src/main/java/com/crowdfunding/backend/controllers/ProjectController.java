package com.crowdfunding.backend.controllers;


import com.crowdfunding.backend.project.CreateProjectRequest;
import com.crowdfunding.backend.project.ProjectDetailResponse;
import com.crowdfunding.backend.project.ProjectListResponse;
import com.crowdfunding.backend.project.ProjectResponse;
import com.crowdfunding.backend.services.ProjectService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    // Inyección de dependencias para lectura y escritura (Service)
    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    // GET /api/projects - Obtener todos los proyectos
    @GetMapping
    public ResponseEntity<List<ProjectListResponse>> getAllProjects() {
        List<ProjectListResponse> projects = projectService.getAllProjects();
        return ResponseEntity.ok(projects);
    }

    // GET /api/projects/{id} - Obtener un proyecto específico por ID
    @GetMapping("/{id}")
    public ResponseEntity<ProjectDetailResponse> getProjectById(@PathVariable UUID id) {
        ProjectDetailResponse project = projectService.getProjectById(id);
        return ResponseEntity.ok(project);
    }


// POST /api/projects - Crear un nuevo proyecto
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse createdProject = projectService.createProject(request);
        return new ResponseEntity<>(createdProject, HttpStatus.CREATED);
    }
}