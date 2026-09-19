package com.example.detection;

import com.example.detection.dto.AmlRequests.*;
import com.example.detection.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="AML_PERFORMANCE_TEST",matches="true")
@SpringBootTest
class IngestionPerformanceTest {
    @Autowired IngestionService ingestion;
    @Autowired BatchService batch;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Test void tenThousandTransactionsWithStructuringUnderTwoMinutes() {
        String prefix=UUID.randomUUID().toString();
        for(int i=0;i<100;i++) {
            String id=prefix+"-"+i;
            ingestion.customer(new CustomerInput(id,"Synthetic","Benchmark",null,null,null,null,null,"IN",null,null,null,LocalDate.of(2020,1,1),null,"VERIFIED","LOW",false));
            ingestion.account(new AccountInput(id,id,"SAVINGS","ACTIVE","USD",LocalDate.of(2020,1,1),null,null,null,BigDecimal.ZERO,"LOW"));
        }
        List<TransactionInput> records=new ArrayList<>();Instant start=Instant.now().minusSeconds(20000);
        for(int i=0;i<10000;i++) records.add(new TransactionInput(prefix+"-t"+i,prefix+"-"+(i%100),new BigDecimal(i>=9700?"9500":"25"),"USD","OUT","SYNTHETIC","BANK",start.plusSeconds(i),"IN"));
        long before=System.nanoTime();var result=batch.transactions(records);double seconds=(System.nanoTime()-before)/1_000_000_000.0;
        System.out.printf("AML benchmark: 10000 transactions, 100 customers, 100 structuring patterns, %.3f seconds%n",seconds);
        assertEquals(100,jdbc.queryForObject("select count(*) from alert_rules r join alerts a on a.id=r.alert_id where a.customer_id like ? and r.rule_code='STRUCTURING'",Integer.class,prefix+"%"));
        assertEquals(10000,result.get("accepted"));assertEquals(0,result.get("rejected"));assertTrue(seconds<120,"Bulk ingestion took "+seconds+" seconds");
    }
}
