package com.crowdfunding.backend.controllers;

import com.crowdfunding.backend.project.CreateProjectRequest;
import com.crowdfunding.backend.project.ProjectDetailResponse;
import com.crowdfunding.backend.project.ProjectListResponse;
import com.crowdfunding.backend.project.ProjectResponse;
import com.crowdfunding.backend.project.PublishProjectResponse;
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

    // GET /api/projects/me - Obtener los proyectos del usuario autenticado
    @GetMapping("/me")
    public ResponseEntity<List<ProjectListResponse>> getMyProjects() {
        List<ProjectListResponse> myProjects = projectService.getMyProjects();
        return ResponseEntity.ok(myProjects);
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

    // POST /api/v1/projects/{id}/publish - Publicar un proyecto (DRAFT -> ACTIVE)
    @PostMapping("/{id}/publish")
    public ResponseEntity<PublishProjectResponse> publishProject(@PathVariable UUID id) {
        PublishProjectResponse response = projectService.publishProject(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletedProject(@PathVariable UUID id){
        projectService.deletedProject(id);
        return ResponseEntity.noContent().build();
        
    }
}