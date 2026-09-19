package com.example.detection.service;

import com.example.detection.dto.AmlRequests.*;
import com.example.detection.repository.MonitoringRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static com.example.detection.service.IngestionService.*;

@Service
@RequiredArgsConstructor
public class ConfigurationService {
    private final JdbcTemplate jdbc;
    private final MonitoringRepository monitoring;
    public List<DetectionEngine.Rule> rules() {return monitoring.rules();}
    public Map<String,BigDecimal> rates() {return monitoring.rates();}
    public List<Map<String,Object>> watchlist() {return jdbc.queryForList("select * from risk_watchlist order by kind,value");}
    @Transactional
    public void rule(String code,RuleInput input,String actor) {
        if(code.equals("STRUCTURING") && input.threshold().compareTo(input.secondaryThreshold())>=0) throw bad("Structuring upper threshold must exceed its lower threshold");
        if(code.equals("RAPID_MOVEMENT") && input.threshold().compareTo(BigDecimal.ONE)>0) throw bad("Rapid movement ratio must be at most 1");
        if(code.equals("HIGH_RISK") && !input.enabled()) throw bad("High-risk watchlist screening must remain enabled");
        if(jdbc.update("update detection_rules set enabled=?,threshold=?,secondary_threshold=?,window_hours=?,minimum_count=?,weight=? where code=?",
            input.enabled(),input.threshold(),input.secondaryThreshold(),input.windowHours(),input.minimumCount(),input.weight(),code)==0) throw missing("Rule");
        monitoring.audit("RULE",code,null,"UPDATED",input.toString(),actor);
    }
    @Transactional
    public void rate(String currency,RateInput input,String actor) {
        if(!currency.matches("[A-Z]{3}")) throw bad("Currency must be a three-letter uppercase code");
        if(currency.equals("INR") && input.inrPerUnit().compareTo(BigDecimal.ONE)!=0) throw bad("INR is the base currency and must have rate 1");
        jdbc.update("insert into exchange_rates(currency,inr_per_unit) values(?,?) on conflict(currency) do update set inr_per_unit=excluded.inr_per_unit,updated_at=now()",currency,input.inrPerUnit());
        monitoring.audit("EXCHANGE_RATE",currency,null,"UPDATED",input.toString(),actor);
    }
    @Transactional
    public void watch(WatchInput input,String actor) {
        String value=input.value().strip().toUpperCase(Locale.ROOT);
        if(input.kind().equals("JURISDICTION") && !value.matches("[A-Z]{2}")) throw bad("Jurisdiction must be a two-letter country code");
        jdbc.update("insert into risk_watchlist(kind,value,enabled) values(?,?,?) on conflict(kind,value) do update set enabled=excluded.enabled",input.kind(),value,input.enabled());
        monitoring.audit("WATCHLIST",input.kind(),null,input.enabled()?"ENABLED":"DISABLED",value,actor);
    }
}
