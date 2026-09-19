package com.example.detection;

import com.example.detection.service.DetectionEngine;
import com.example.detection.service.DetectionEngine.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DetectionEngineTest {
    private final DetectionEngine engine=new DetectionEngine();
    private final Instant now=Instant.parse("2026-07-20T12:00:00Z");
    private Event event(String id,String account,String amount,String direction,Instant time) {
        BigDecimal usd=new BigDecimal(amount);return new Event(id,account,usd.multiply(new BigDecimal("83")),usd,direction,"SYNTHETIC","IN",time);
    }
    private Rule rule(String code,String threshold,String second,int hours,int count) {return new Rule(code,true,new BigDecimal(threshold),new BigDecimal(second),hours,count,40);}
    private List<Hit> evaluate(Event current,List<Event> events,Rule r) {return engine.evaluate(current,events,List.of(r),Set.of());}
    @Test void reportingThresholdIncludesExactBoundary() {
        var r=rule("THRESHOLD","10000","1",24,1);
        var at=event("t","a","10000","OUT",now); assertEquals(1,evaluate(at,List.of(at),r).size());
        var below=event("t","a","9999.99","OUT",now);assertTrue(evaluate(below,List.of(below),r).isEmpty());
    }
    @Test void structuringUsesAccountWindowAndLowerInclusiveUpperExclusiveBounds() {
        var r=rule("STRUCTURING","9000","10000",24,3);
        var a=event("a","one","9000","OUT",now.minus(Duration.ofHours(24)));
        var b=event("b","one","9999.99","OUT",now.minusSeconds(5));
        var c=event("c","one","9500","OUT",now);
        assertEquals(Set.of("a","b","c"),new HashSet<>(evaluate(c,List.of(a,b,c),r).get(0).evidence()));
        assertTrue(evaluate(c,List.of(a,c,event("other","two","9500","OUT",now)),r).isEmpty());
        assertTrue(evaluate(c,List.of(b,c,event("old","one","9500","OUT",now.minus(Duration.ofHours(24)).minusSeconds(1))),r).isEmpty());
        assertTrue(evaluate(c,List.of(b,c,event("exact","one","10000","OUT",now)),r).isEmpty());
    }
    @Test void rapidMovementRequiresLaterOutflowsAndIncludesEightyPercent() {
        var r=rule("RAPID_MOVEMENT","0.8","1",48,1);
        var deposit=event("in","a","1000","IN",now.minusSeconds(60));
        var out=event("out","a","800","OUT",now);
        assertEquals(1,evaluate(out,List.of(deposit,out),r).size());
        assertTrue(evaluate(deposit,List.of(event("old","a","900","OUT",now.minusSeconds(120)),deposit),r).isEmpty());
        assertTrue(evaluate(out,List.of(event("in","a","1001","IN",now.minusSeconds(60)),out),r).isEmpty());
    }
    @Test void rapidMovementCombinesSplitOutflows() {
        var r=rule("RAPID_MOVEMENT","0.8","1",48,1);
        var in=event("in","a","1000","IN",now.minusSeconds(60));var out=event("out","a","400","OUT",now);
        assertEquals(1,evaluate(out,List.of(in,out,event("split","a","400","OUT",now.minusSeconds(20))),r).size());
    }
    @Test void watchlistMatchesCountryOrCaseInsensitiveCounterpartyAtAnyAmount() {
        var r=rule("HIGH_RISK","1","1",24,1);var small=event("t","a","0.01","OUT",now);
        assertEquals(1,engine.evaluate(small,List.of(small),List.of(r),Set.of("JURISDICTION:IN")).size());
        assertEquals(1,engine.evaluate(small,List.of(small),List.of(r),Set.of("COUNTERPARTY:SYNTHETIC")).size());
        assertTrue(evaluate(small,List.of(small),r).isEmpty());
    }
    @Test void behavioralAggregatesCustomerAccountsAndExcludesCurrentDayFromBaseline() {
        var r=rule("BEHAVIORAL","3","1",2160,1);List<Event> history=new ArrayList<>();
        for(int i=1;i<=90;i++) history.add(event("old"+i,"a","100","IN",now.minus(Duration.ofDays(i))));
        var current=event("today","b","301","OUT",now);history.add(current);
        assertEquals(1,evaluate(current,history,r).size());
        history.set(history.size()-1,event("today","b","300","OUT",now));assertTrue(evaluate(history.get(history.size()-1),history,r).isEmpty());
        assertTrue(evaluate(current,List.of(current),r).isEmpty());
    }
    @Test void behavioralCountAloneCanTrigger() {
        var r=rule("BEHAVIORAL","3","1",2160,1);List<Event> history=new ArrayList<>();
        for(int i=1;i<=90;i++) history.add(event("old"+i,"a","10000","IN",now.minus(Duration.ofDays(i))));
        for(int i=0;i<4;i++) history.add(event("new"+i,"b","1","OUT",now));
        assertEquals(1,evaluate(history.get(history.size()-1),history,r).size());
    }
    @Test void repeatedRoundAmountsAndDisabledRules() {
        var r=rule("ROUND_NUMBER","1000","1",24,3);
        var a=event("a","a","2000","OUT",now);var b=event("b","a","3000","OUT",now);var c=event("c","a","1000","OUT",now);
        assertEquals(1,evaluate(c,List.of(a,b,c),r).size());
        assertTrue(evaluate(c,List.of(a,b,c),new Rule(r.code(),false,r.threshold(),r.secondaryThreshold(),24,3,40)).isEmpty());
    }
    @Test void futureEventsNeverContaminateRuleWindow() {
        var current=event("c","a","9500","OUT",now);
        assertTrue(evaluate(current,List.of(current,event("future1","a","9500","OUT",now.plusSeconds(1)),event("future2","a","9500","OUT",now.plusSeconds(2))),rule("STRUCTURING","9000","10000",24,3)).isEmpty());
    }
}
