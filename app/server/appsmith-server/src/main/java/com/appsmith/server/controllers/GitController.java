package com.appsmith.server.controllers;

import com.appsmith.external.dtos.GitLogDTO;
import com.appsmith.server.constants.Url;
import com.appsmith.server.domains.Application;
import com.appsmith.server.domains.GitProfile;
import com.appsmith.server.dtos.GitBranchDTO;
import com.appsmith.server.dtos.GitCommitDTO;
import com.appsmith.server.dtos.GitConnectDTO;
import com.appsmith.server.dtos.ResponseDTO;
import com.appsmith.server.services.GitService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Ref;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping(Url.GIT_URL)
public class GitController {

    private final GitService service;

    @Autowired
    public GitController(GitService service) {
        this.service = service;
    }

    @PostMapping("/config/save")
    public Mono<ResponseDTO<Map<String, GitProfile>>> saveGitConfigData(@RequestBody GitProfile gitProfile) {
        //Add to the userData object - git config data
        return service.updateOrCreateGitProfileForCurrentUser(gitProfile)
                .map(response -> new ResponseDTO<>(HttpStatus.OK.value(), response, null));
    }

    @PutMapping("/config/{defaultApplicationId}")
    public Mono<ResponseDTO<Map<String, GitProfile>>> saveGitConfigData(@PathVariable String defaultApplicationId,
                                                                        @RequestBody GitProfile gitProfile) {
        //Add to the userData object - git config data
        return service.updateOrCreateGitProfileForCurrentUser(gitProfile, Boolean.FALSE, defaultApplicationId)
                .map(response -> new ResponseDTO<>(HttpStatus.OK.value(), response, null));
    }

    @GetMapping("/config")
    public Mono<ResponseDTO<GitProfile>> getDefaultGitConfigForUser() {
        return service.getGitProfileForUser()
                .map(gitConfigResponse -> new ResponseDTO<>(HttpStatus.OK.value(), gitConfigResponse, null));
    }

    @GetMapping("/config/{defaultApplicationId}")
    public Mono<ResponseDTO<GitProfile>> getGitConfigForUser(@PathVariable String defaultApplicationId) {
        return service.getGitProfileForUser(defaultApplicationId)
                .map(gitConfigResponse -> new ResponseDTO<>(HttpStatus.OK.value(), gitConfigResponse, null));
    }

    @PostMapping("/connect/{applicationId}")
    public Mono<ResponseDTO<Application>> connectApplicationToRemoteRepo(@PathVariable String applicationId,
                                                                         @RequestBody GitConnectDTO gitConnectDTO,
                                                                         @RequestHeader("Origin") String originHeader) {
        return service.connectApplicationToGit(applicationId, gitConnectDTO, originHeader)
                .map(application -> new ResponseDTO<>(HttpStatus.OK.value(), application, null));
    }

    @PostMapping("/commit/{applicationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseDTO<String>> commit(@RequestBody GitCommitDTO commitDTO, @PathVariable String applicationId) {
        log.debug("Going to commit application {}", applicationId);
        return service.commitApplication(commitDTO, applicationId)
                .map(result -> new ResponseDTO<>(HttpStatus.CREATED.value(), result, null));
    }

    @GetMapping("/commit-history/{applicationId}")
    public Mono<ResponseDTO<List<GitLogDTO>>> getCommitHistory(@PathVariable String applicationId) {
        log.debug("Fetching commit-history for application {}", applicationId);
        return service.getCommitHistory(applicationId)
                .map(logs -> new ResponseDTO<>(HttpStatus.CREATED.value(), logs, null));
    }

    @PostMapping("/push/{applicationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseDTO<String>> push(@PathVariable String applicationId) {
        log.debug("Going to push application {}", applicationId);
        return service.pushApplication(applicationId)
                .map(result -> new ResponseDTO<>(HttpStatus.CREATED.value(), result, null));
    }

    @PostMapping("/create-branch/{srcApplicationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseDTO<Application>> createBranch(@RequestBody GitBranchDTO branchDTO,
                                                       @PathVariable String srcApplicationId) {
        log.debug("Going to push application {}", srcApplicationId);
        return service.createBranch(srcApplicationId, branchDTO)
                .map(result -> new ResponseDTO<>(HttpStatus.CREATED.value(), result, null));
    }

    @PostMapping("/disconnect/{applicationId}")
    public Mono<ResponseDTO<Application>> disconnectFromRemote(@PathVariable String applicationId) {
        log.debug("Going to update the remoteUrl for application {}", applicationId);
        return service.detachRemote(applicationId)
                .map(result -> new ResponseDTO<>(HttpStatus.OK.value(), result, null));
    }

    @GetMapping("/pull/{applicationId}/{branchName}")
    public Mono<ResponseDTO<String>> pull(@PathVariable String applicationId, String branchName) {
        log.debug("Going to pull the latest for branch");
        return service.pullForApplication(applicationId, branchName)
                .map(result -> new ResponseDTO<>(HttpStatus.OK.value(), result, null));
    }

    @GetMapping("/branch/{applicationId}")
    public Mono<ResponseDTO<List<String>>> branch(@PathVariable String applicationId) {
        return service.listBranchForApplication(applicationId)
                .map(result -> new ResponseDTO<>(HttpStatus.OK.value(), result, null));
    }
}
