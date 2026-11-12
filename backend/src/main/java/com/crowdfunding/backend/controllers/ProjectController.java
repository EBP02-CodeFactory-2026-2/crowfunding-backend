package com.crowdfunding.backend.controllers;

import com.crowdfunding.backend.project.CreateProjectRequest;
import com.crowdfunding.backend.project.EditProjectRequest;
import com.crowdfunding.backend.project.EditProjectResponse;
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

    // GET /api/v1/projects - Listar y filtrar proyectos activos por etiqueta de riesgo (HU-1.3.2, HU-2.3.5)
    @GetMapping
    public ResponseEntity<List<ProjectListResponse>> getAllProjects(
            @RequestParam(required = false) List<String> riskTag
    ) {
        List<ProjectListResponse> projects = projectService.getProjects(riskTag);
        return ResponseEntity.ok(projects);
    }

    // GET /api/v1/projects/me - Obtener los proyectos del usuario autenticado (HU-1.1.6)
    @GetMapping("/me")
    public ResponseEntity<List<ProjectListResponse>> getMyProjects() {
        List<ProjectListResponse> myProjects = projectService.getMyProjects();
        return ResponseEntity.ok(myProjects);
    }

    // GET /api/v1/projects/{id} - Obtener un proyecto específico por ID
    @GetMapping("/{id}")
    public ResponseEntity<ProjectDetailResponse> getProjectById(@PathVariable UUID id) {
        ProjectDetailResponse project = projectService.getProjectById(id);
        return ResponseEntity.ok(project);
    }

    // POST /api/v1/projects - Crear un nuevo proyecto
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

    // DELETE /api/v1/projects/{id} - Eliminar un proyecto sin aportes
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    // PUT /api/v1/projects/{id} - Editar un proyecto
    @PutMapping("/{id}")
    public ResponseEntity<EditProjectResponse> editProject(@PathVariable UUID id, @Valid @RequestBody EditProjectRequest request) {
        EditProjectResponse response = projectService.editProject(id, request);
        return ResponseEntity.ok(response);
    }
}