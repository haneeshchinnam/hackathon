package com.example.detection.controller;

import com.example.detection.dto.AmlRequests.*;
import com.example.detection.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','ANALYST')")
public class MonitoringController {
    private final QueryService queries;
    private final AlertService workflow;
    @GetMapping("/customers") @PreAuthorize("hasAnyRole('USER','ADMIN','ANALYST','INGESTOR')")
    public List<Map<String,Object>> customers(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return queries.customers(page,size);}
    @GetMapping("/customers/{id}") public Map<String,Object> customer(@PathVariable String id) {return queries.customer(id);}
    @GetMapping("/accounts") public List<Map<String,Object>> accounts(@RequestParam(required=false) String customerId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return queries.accounts(customerId,page,size);}
    @GetMapping("/accounts/{id}") public Map<String,Object> account(@PathVariable String id) {return queries.account(id);}
    @GetMapping("/transactions") public List<Map<String,Object>> transactions(@RequestParam(required=false) String customerId,@RequestParam(required=false) String accountId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return queries.transactions(customerId,accountId,page,size);}
    @GetMapping("/transactions/{id}") public Map<String,Object> transaction(@PathVariable String id) {return queries.transaction(id);}
    @GetMapping("/alerts") public List<Map<String,Object>> alerts(@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return queries.alerts(status,page,size);}
    @GetMapping("/alerts/{id}") public Map<String,Object> alert(@PathVariable long id,@RequestParam(defaultValue="0") int evidencePage,@RequestParam(defaultValue="50") int evidenceSize) {return queries.alert(id,evidencePage,evidenceSize);}
    @PatchMapping("/alerts/{id}/disposition") public Map<String,Object> disposition(@PathVariable long id,@Valid @RequestBody Disposition input,Authentication auth) {return workflow.disposition(id,input,auth.getName());}
    @GetMapping("/alerts/{id}/audit") public List<Map<String,Object>> alertAudit(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return queries.audit("ALERT",id,page,size);}
    @GetMapping("/cases") public List<Map<String,Object>> cases(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return queries.cases(page,size);}
    @PostMapping("/cases") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createCase(@Valid @RequestBody CaseInput input,Authentication auth) {return workflow.createCase(input,auth.getName());}
    @GetMapping("/cases/{id}") public Map<String,Object> caseDetail(@PathVariable long id) {return queries.caseDetail(id);}
    @PatchMapping("/cases/{id}") public Map<String,Object> updateCase(@PathVariable long id,@Valid @RequestBody CaseUpdate input,Authentication auth) {return workflow.updateCase(id,input,auth.getName());}
    @GetMapping("/cases/{id}/audit") public List<Map<String,Object>> caseAudit(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return queries.audit("CASE",id,page,size);}
}
