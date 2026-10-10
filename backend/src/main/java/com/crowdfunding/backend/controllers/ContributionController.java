package com.crowdfunding.backend.controllers;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.crowdfunding.backend.contribution.ContributionRequest;
import com.crowdfunding.backend.contribution.ContributionResponse;
import com.crowdfunding.backend.services.ContributionService;

import jakarta.validation.Valid;

@RestController 
@RequestMapping("/api/v1/projects")
public class ContributionController {
    final ContributionService contributionService;

    ContributionController(ContributionService contributionService) {
        this.contributionService = contributionService;
    }

    @PostMapping("/{projectId}/contributions")
    public ResponseEntity<ContributionResponse> supportProject(@PathVariable UUID projectId, @Valid @RequestBody ContributionRequest request){
        ContributionResponse response = contributionService.supportProject(projectId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
