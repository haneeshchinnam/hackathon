package com.example.detection.service;

import com.example.detection.dto.AmlRequests.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.example.detection.service.IngestionService.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchService {
    private final IngestionService ingestion;
    @Transactional
    public Map<String,Object> customers(List<CustomerInput> records) {
        int row=0;
        try {for(var input:records) {row++; ingestion.customer(input);}}
        catch(RuntimeException ex) {log.warn("Customer ingestion rejected at record {} ({})",row,ex.getClass().getSimpleName()); throw ex;}
        return Map.of("accepted",records.size());
    }
    @Transactional
    public Map<String,Object> accounts(List<AccountInput> records) {
        int row=0;
        try {for(var input:records) {row++; ingestion.account(input);}}
        catch(RuntimeException ex) {log.warn("Account ingestion rejected at record {} ({})",row,ex.getClass().getSimpleName()); throw ex;}
        return Map.of("accepted",records.size());
    }
    // Per-record transactions: one bad row never rolls back earlier successful transactions.
    public Map<String,Object> transactions(List<TransactionInput> records) {
        List<Map<String,Object>> results=new ArrayList<>(); int row=0,accepted=0;
        for(var input:records) {
            row++;
            try {var result=new LinkedHashMap<>(ingestion.transaction(input));result.put("row",row);results.add(result);accepted++;}
            catch(RuntimeException ex) {
                if(!(ex instanceof org.springframework.web.server.ResponseStatusException || ex instanceof jakarta.validation.ConstraintViolationException || ex instanceof org.springframework.dao.DataIntegrityViolationException)) throw ex;
                log.warn("Transaction ingestion rejected at record {} ({})",row,ex.getClass().getSimpleName());
                String reason=ex instanceof org.springframework.web.server.ResponseStatusException status ? status.getReason() : "Invalid record or conflicting ID";
                results.add(Map.of("row",row,"id",input.id(),"error",reason));
            }
        }
        return Map.of("accepted",accepted,"rejected",records.size()-accepted,"results",results);
    }
    public Map<String,Object> csv(String kind,MultipartFile file) {
        if(file.isEmpty()) throw bad("CSV file is empty");
        try(Reader reader=new InputStreamReader(file.getInputStream(),StandardCharsets.UTF_8);
            CSVParser parser=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setTrim(true).get().parse(reader)) {
            List<CustomerInput> customers=new ArrayList<>(); List<AccountInput> accounts=new ArrayList<>(); List<TransactionInput> transactions=new ArrayList<>();
            long row=1;
            try {
                for(CSVRecord r:parser) {
                    row=r.getRecordNumber()+1;
                    if(row>10001) throw bad("CSV limit is 10000 records");
                    switch(kind) {
                        case "customers" -> customers.add(new CustomerInput(required(r,"customer_id"),required(r,"first_name"),required(r,"last_name"),
                            date(r,"date_of_birth"),optional(r,"email"),optional(r,"phone_number"),optional(r,"city"),optional(r,"state"),required(r,"country"),
                            optional(r,"postal_code"),optional(r,"occupation"),decimal(r,"annual_income"),date(r,"customer_since"),optional(r,"customer_segment"),
                            required(r,"kyc_status"),required(r,"risk_rating"),flag(r,"is_politically_exposed")));
                        case "accounts" -> accounts.add(new AccountInput(required(r,"account_id"),required(r,"customer_id"),required(r,"account_type"),
                            required(r,"account_status"),required(r,"currency"),date(r,"open_date"),date(r,"close_date"),optional(r,"branch_code"),optional(r,"branch_city"),
                            decimal(r,"current_balance"),optional(r,"risk_rating")==null?"LOW":required(r,"risk_rating")));
                        case "transactions" -> transactions.add(new TransactionInput(required(r,"transaction_id"),required(r,"account_id"),decimal(r,"amount"),
                            required(r,"currency"),required(r,"direction"),required(r,"counterparty"),required(r,"channel"),Instant.parse(required(r,"timestamp")),required(r,"jurisdiction")));
                        default -> throw bad("CSV resource must be customers, accounts, or transactions");
                    }
                }
            } catch(IllegalArgumentException|java.time.format.DateTimeParseException ex) {log.warn("Malformed {} CSV at row {}",kind,row);throw bad("Malformed CSV at row "+row+"; check headers and field formats");}
            if(row==1) throw bad("CSV has no records");
            // Use a separate transactional proxy through the controller for atomic customer/account imports.
            return Map.of("customers",customers,"accounts",accounts,"transactions",transactions);
        } catch(IOException|UncheckedIOException ex) {throw bad("Could not parse CSV");}
    }
    private String required(CSVRecord r,String key) {String v=optional(r,key);if(v==null) throw new IllegalArgumentException("Missing field");return v;}
    private String optional(CSVRecord r,String key) {return !r.isMapped(key)||r.get(key).isBlank()?null:r.get(key);}
    private LocalDate date(CSVRecord r,String key) {String v=optional(r,key);return v==null?null:LocalDate.parse(v);}
    private BigDecimal decimal(CSVRecord r,String key) {String v=optional(r,key);return v==null?null:new BigDecimal(v);}
    private boolean flag(CSVRecord r,String key) {
        String v=optional(r,key);if(v==null||Set.of("0","N","false").contains(v))return false;
        if(Set.of("1","Y","true").contains(v))return true;throw new IllegalArgumentException("Invalid boolean");
    }
}
