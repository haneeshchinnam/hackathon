package com.example.detection.service;

import com.example.detection.dto.AmlRequests.*;
import com.example.detection.model.*;
import com.example.detection.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.example.detection.service.IngestionService.*;

@Service
@RequiredArgsConstructor
public class AlertService {
    private final AlertRepository alerts;
    private final CustomerRepository customers;
    private final InvestigationCaseRepository cases;
    private final MonitoringRepository monitoring;
    private final JdbcTemplate jdbc;

    /** Called within the ingestion transaction while the customer lock is held. */
    public Long aggregate(Customer customer,List<DetectionEngine.Hit> hits) {
        if(hits.isEmpty()) return null;
        List<Long> active=jdbc.queryForList("select id from alerts where customer_id=? and status in ('OPEN','IN_REVIEW')",Long.class,customer.getId());
        Alert alert;
        boolean created=active.isEmpty();
        if(created) {
            alert=new Alert(); alert.setCustomer(customer); alert.setStatus("OPEN"); alert.setCreatedAt(Instant.now());
            alert.setUpdatedAt(Instant.now()); alert.setExplanation("Detection pending"); alerts.saveAndFlush(alert);
        } else alert=alerts.findById(active.get(0)).orElseThrow();
        Map<String,DetectionEngine.Hit> byRule=new LinkedHashMap<>();
        Set<String> evidence=new HashSet<>();
        for(var hit:hits) { byRule.put(hit.rule().code(),hit); evidence.addAll(hit.evidence()); }
        for(var hit:byRule.values()) jdbc.update("""
            insert into alert_rules(alert_id,rule_code,weight,explanation,configuration) values(?,?,?,?,?)
            on conflict(alert_id,rule_code) do update set weight=excluded.weight,explanation=excluded.explanation,configuration=excluded.configuration
            """,alert.getId(),hit.rule().code(),hit.rule().weight(),hit.explanation(),hit.rule().toString());
        jdbc.batchUpdate("insert into alert_evidence(alert_id,transaction_id) values(?,?) on conflict do nothing",evidence,500,
            (ps,id)->{ps.setLong(1,alert.getId());ps.setString(2,id);});
        Integer score=jdbc.queryForObject("select least(100,sum(weight)) from alert_rules where alert_id=?",Integer.class,alert.getId());
        List<String> reasons=jdbc.queryForList("select explanation from alert_rules where alert_id=? order by rule_code",String.class,alert.getId());
        alert.setRiskScore(score); alert.setExplanation(String.join("; ",reasons)); alert.setUpdatedAt(Instant.now()); alerts.saveAndFlush(alert);
        monitoring.audit("ALERT",alert.getId(),created?null:alert.getStatus(),alert.getStatus(),created?"Detection created alert":"Evidence aggregated", "SYSTEM");
        return alert.getId();
    }
    @Transactional
    public Map<String,Object> disposition(long id,Disposition input,String actor) {
        Alert alert=alerts.findById(id).orElseThrow(()->missing("Alert"));
        customers.lockById(alert.getCustomer().getId()).orElseThrow();
        // Refresh after obtaining the shared customer lock; another analyst may have changed the state.
        jdbc.queryForObject("select id from alerts where id=? for update",Long.class,id);
        String previous=jdbc.queryForObject("select status from alerts where id=?",String.class,id);
        if(!Set.of("OPEN","IN_REVIEW").contains(previous)) throw conflict("Disposed alerts cannot change state");
        if(previous.equals(input.status())) throw conflict("Alert is already in this state");
        jdbc.update("update alerts set status=?,disposition_reason=?,analyst=?,updated_at=now() where id=?",input.status(),input.reason(),actor,id);
        monitoring.audit("ALERT",id,previous,input.status(),input.reason(),actor);
        return Map.of("id",id,"status",input.status());
    }
    @Transactional
    public Map<String,Object> createCase(CaseInput input,String actor) {
        List<Long> ids=input.alertIds().stream().distinct().sorted().toList();
        Alert first=alerts.findById(ids.get(0)).orElseThrow(()->missing("Alert"));
        Customer customer=customers.lockById(first.getCustomer().getId()).orElseThrow();
        for(Long id:ids) {
            Alert alert=alerts.findById(id).orElseThrow(()->missing("Alert"));
            if(!alert.getCustomer().getId().equals(customer.getId())) throw bad("Case alerts must belong to the same customer");
            if(jdbc.queryForObject("select count(*) from case_alerts where alert_id=?",Integer.class,id)>0) throw conflict("Alert already belongs to a case");
        }
        checkAssignee(input.assignedTo());
        InvestigationCase entity=new InvestigationCase(); entity.setCustomer(customer); entity.setTitle(input.title());
        entity.setStatus("OPEN"); entity.setAssignedTo(input.assignedTo()); entity.setCreatedAt(Instant.now()); entity.setUpdatedAt(Instant.now()); cases.saveAndFlush(entity);
        for(Long id:ids) jdbc.update("insert into case_alerts(case_id,alert_id) values(?,?)",entity.getId(),id);
        monitoring.audit("CASE",entity.getId(),null,"OPEN","Case created",actor);
        return Map.of("id",entity.getId(),"status","OPEN");
    }
    @Transactional
    public Map<String,Object> updateCase(long id,CaseUpdate input,String actor) {
        List<String> states=jdbc.queryForList("select status from investigation_cases where id=? for update",String.class,id);
        if(states.isEmpty()) throw missing("Case");
        String previous=states.get(0);
        if(previous.equals("CLOSED")) throw conflict("Closed cases are immutable");
        if(previous.equals("IN_PROGRESS") && input.status().equals("OPEN")) throw conflict("Case cannot return to OPEN");
        checkAssignee(input.assignedTo());
        jdbc.update("update investigation_cases set status=?,assigned_to=?,disposition_reason=?,updated_at=now() where id=?",input.status(),input.assignedTo(),input.reason(),id);
        monitoring.audit("CASE",id,previous,input.status(),input.reason(),actor);
        return Map.of("id",id,"status",input.status());
    }
    private void checkAssignee(String username) {
        if(username!=null && jdbc.queryForObject("select count(*) from user_roles r join users u on u.id=r.user_id where u.username=? and r.role in ('ROLE_ADMIN','ROLE_ANALYST')",Integer.class,username)==0)
            throw bad("Assignee must be an existing analyst or administrator");
    }
}
