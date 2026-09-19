package com.example.detection.controller;

import com.example.detection.dto.AmlRequests.*;
import com.example.detection.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','INGESTOR')")
public class IngestionController {
    private final IngestionService ingestion;
    private final BatchService batch;
    @PostMapping("/customers") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,String> customer(@Valid @RequestBody CustomerInput input) {return Map.of("id",ingestion.customer(input).getId());}
    @PostMapping("/accounts") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,String> account(@Valid @RequestBody AccountInput input) {return Map.of("id",ingestion.account(input).getId());}
    @PostMapping("/transactions")
    public ResponseEntity<Map<String,Object>> transaction(@Valid @RequestBody TransactionInput input) {
        var result=ingestion.transaction(input); return ResponseEntity.status(Boolean.TRUE.equals(result.get("duplicate"))?HttpStatus.OK:HttpStatus.CREATED).body(result);
    }
    @PostMapping("/customers/batch") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> customers(@Valid @RequestBody Batch<CustomerInput> input) {return batch.customers(input.records());}
    @PostMapping("/accounts/batch") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> accounts(@Valid @RequestBody Batch<AccountInput> input) {return batch.accounts(input.records());}
    @PostMapping("/transactions/batch")
    public Map<String,Object> transactions(@Valid @RequestBody Batch<TransactionInput> input) {return batch.transactions(input.records());}
    @PostMapping(value="/imports/{resource}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @SuppressWarnings("unchecked")
    public Map<String,Object> csv(@PathVariable String resource,@RequestParam MultipartFile file) {
        var records=batch.csv(resource,file);
        return switch(resource) {
            case "customers" -> batch.customers((List<CustomerInput>)records.get(resource));
            case "accounts" -> batch.accounts((List<AccountInput>)records.get(resource));
            case "transactions" -> batch.transactions((List<TransactionInput>)records.get(resource));
            default -> throw IngestionService.bad("Unknown import resource");
        };
    }
}
