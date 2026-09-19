package com.example.detection.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.example.detection.service.IngestionService.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class QueryService {
    private final JdbcTemplate jdbc;
    public List<Map<String,Object>> customers(int page,int size) {
        return jdbc.queryForList("""
            select id, left(first_name,1)||'***' as first_name,left(last_name,1)||'***' as last_name,
            country,kyc_status,risk_rating,politically_exposed from customers order by id limit ? offset ?
            """,size,offset(page,size));
    }
    public Map<String,Object> customer(String id) { return one("select * from customers where id=?",id); }
    public List<Map<String,Object>> accounts(String customer,int page,int size) {
        return jdbc.queryForList("select * from accounts where (cast(? as varchar) is null or customer_id=?) order by id limit ? offset ?",customer,customer,size,offset(page,size));
    }
    public Map<String,Object> account(String id) {return one("select * from accounts where id=?",id);}
    public List<Map<String,Object>> transactions(String customer,String account,int page,int size) {
        return jdbc.queryForList("""
            select t.id,t.account_id,t.amount,t.currency,t.base_amount,t.direction,
            left(t.counterparty,1)||'***' as counterparty,t.channel,t.occurred_at,t.jurisdiction
            from transactions t join accounts a on a.id=t.account_id
            where (cast(? as varchar) is null or a.customer_id=?) and (cast(? as varchar) is null or t.account_id=?)
            order by t.occurred_at desc,t.id limit ? offset ?
            """,customer,customer,account,account,size,offset(page,size));
    }
    public Map<String,Object> transaction(String id) {return one("select * from transactions where id=?",id);}
    public List<Map<String,Object>> alerts(String status,int page,int size) {
        return jdbc.queryForList("select id,customer_id,status,risk_score,created_at,updated_at from alerts where (cast(? as varchar) is null or status=?) order by risk_score desc,created_at,id limit ? offset ?",status,status,size,offset(page,size));
    }
    public Map<String,Object> alert(long id,int page,int size) {
        Map<String,Object> result=one("select * from alerts where id=?",id);
        result.put("rules",jdbc.queryForList("select rule_code,weight,explanation,configuration from alert_rules where alert_id=? order by rule_code",id));
        result.put("evidence",jdbc.queryForList("select transaction_id from alert_evidence where alert_id=? order by transaction_id limit ? offset ?",id,size,offset(page,size)));
        result.put("evidenceCount",jdbc.queryForObject("select count(*) from alert_evidence where alert_id=?",Long.class,id));
        return result;
    }
    public List<Map<String,Object>> cases(int page,int size) {
        return jdbc.queryForList("select id,customer_id,status,assigned_to,created_at,updated_at from investigation_cases order by updated_at desc,id limit ? offset ?",size,offset(page,size));
    }
    public Map<String,Object> caseDetail(long id) {
        Map<String,Object> result=one("select * from investigation_cases where id=?",id);
        result.put("alertIds",jdbc.queryForList("select alert_id from case_alerts where case_id=? order by alert_id",Long.class,id)); return result;
    }
    public List<Map<String,Object>> audit(String type,long id,int page,int size) {
        return jdbc.queryForList("select * from audit_events where entity_type=? and entity_id=? order by id limit ? offset ?",type,Long.toString(id),size,offset(page,size));
    }
    private Map<String,Object> one(String sql,Object id) {
        var rows=jdbc.queryForList(sql,id); if(rows.isEmpty()) throw missing("Resource"); return rows.get(0);
    }
    private long offset(int page,int size) {
        if(page<0 || size<1 || size>200) throw bad("page must be nonnegative and size must be 1..200"); return (long)page*size;
    }
}
