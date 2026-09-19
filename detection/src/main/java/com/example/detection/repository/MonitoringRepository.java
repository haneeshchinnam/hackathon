package com.example.detection.repository;

import com.example.detection.service.DetectionEngine.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class MonitoringRepository {
    private final JdbcTemplate jdbc;
    public List<Rule> rules() {
        return jdbc.query("select * from detection_rules order by code", (rs,n) -> new Rule(rs.getString("code"),rs.getBoolean("enabled"),
            rs.getBigDecimal("threshold"),rs.getBigDecimal("secondary_threshold"),rs.getInt("window_hours"),rs.getInt("minimum_count"),rs.getInt("weight")));
    }
    public Set<String> watchlist() {
        return new HashSet<>(jdbc.query("select kind || ':' || value from risk_watchlist where enabled", (rs,n)->rs.getString(1)));
    }
    public List<Event> history(String customerId, Instant from, Instant to) {
        return jdbc.query("""
            select t.* from transactions t join accounts a on a.id=t.account_id
            where a.customer_id=? and t.occurred_at>=? and t.occurred_at<=? order by t.occurred_at,t.id
            """, (rs,n)->new Event(rs.getString("id"),rs.getString("account_id"),rs.getBigDecimal("base_amount"),rs.getBigDecimal("usd_amount"),
                rs.getString("direction"),rs.getString("counterparty"),rs.getString("jurisdiction"),rs.getTimestamp("occurred_at").toInstant()),
            customerId, Timestamp.from(from),Timestamp.from(to));
    }
    public Map<String,BigDecimal> rates() {
        Map<String,BigDecimal> result = new HashMap<>();
        jdbc.query("select currency,inr_per_unit from exchange_rates", rs -> { result.put(rs.getString(1),rs.getBigDecimal(2)); });
        return result;
    }
    public void audit(String type, Object id, String previous, String next, String reason, String actor) {
        jdbc.update("insert into audit_events(entity_type,entity_id,previous_state,new_state,reason,actor) values (?,?,?,?,?,?)",
            type,id.toString(),previous,next,reason,actor);
    }
}
