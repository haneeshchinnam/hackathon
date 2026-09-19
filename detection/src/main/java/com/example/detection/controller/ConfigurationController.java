package com.example.detection.controller;

import com.example.detection.dto.AmlRequests.*;
import com.example.detection.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/config")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ConfigurationController {
    private final ConfigurationService configuration;
    @GetMapping("/rules") public List<DetectionEngine.Rule> rules() {return configuration.rules();}
    @PutMapping("/rules/{code}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rule(@PathVariable String code,@Valid @RequestBody RuleInput input,Authentication auth) {configuration.rule(code,input,auth.getName());}
    @GetMapping("/exchange-rates") public Map<String,BigDecimal> rates() {return configuration.rates();}
    @PutMapping("/exchange-rates/{currency}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rate(@PathVariable String currency,@Valid @RequestBody RateInput input,Authentication auth) {configuration.rate(currency,input,auth.getName());}
    @GetMapping("/watchlist") public List<Map<String,Object>> watchlist() {return configuration.watchlist();}
    @PutMapping("/watchlist") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void watch(@Valid @RequestBody WatchInput input,Authentication auth) {configuration.watch(input,auth.getName());}
}
