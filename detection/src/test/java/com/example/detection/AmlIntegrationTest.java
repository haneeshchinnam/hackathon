package com.example.detection;

import com.example.detection.dto.AmlRequests.*;
import com.example.detection.service.*;
import com.example.detection.model.*;
import com.example.detection.repository.UserRepository;
import com.example.detection.service.TokenService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="AML_INTEGRATION_TESTS",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class AmlIntegrationTest {
    @Autowired IngestionService ingestion;
    @Autowired QueryService queries;
    @Autowired AlertService workflow;
    @Autowired ConfigurationService configuration;
    @Autowired BatchService batch;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired TokenService tokens;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    @LocalServerPort int port;
    private String prefix;
    private String actor;
    private Instant now;
    @BeforeEach void setup() {
        prefix=UUID.randomUUID().toString(); now=Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        actor="a"+prefix.replace("-","").substring(0,15);
        User user=new User(actor,actor+"@example.test","unused-test-hash");user.setRoles(Set.of(Role.ROLE_ANALYST));users.saveAndFlush(user);
        ingestion.customer(customer(prefix)); ingestion.account(account(prefix,prefix));
    }
    private CustomerInput customer(String id) {return new CustomerInput(id,"Synthetic","Person",LocalDate.of(1990,1,1),"synthetic@example.test",null,null,null,"IN",null,null,null,LocalDate.of(2020,1,1),null,"VERIFIED","LOW",false);}
    private AccountInput account(String id,String customer) {return new AccountInput(id,customer,"SAVINGS","ACTIVE","USD",LocalDate.of(2020,1,1),null,null,null,BigDecimal.ZERO,"LOW");}
    private TransactionInput tx(String suffix,String amount,String direction,Instant time) {return new TransactionInput(prefix+suffix,prefix,new BigDecimal(amount),"USD",direction,"SYNTHETIC","BANK",time,"IN");}
    @Test void ingestionNormalizesAggregatesAndDeduplicatesWithImmutableAudit() {
        var input=tx("-1","10000","IN",now);
        var result=ingestion.transaction(input);long id=((Number)result.get("alertId")).longValue();
        assertEquals(new BigDecimal("830000.00"),result.get("baseAmount"));
        assertEquals(true,ingestion.transaction(input).get("duplicate"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->ingestion.transaction(tx("-1","10001","IN",now)));
        ingestion.transaction(tx("-2","12000","IN",now.plusSeconds(1)));
        assertEquals(1,jdbc.queryForObject("select count(*) from alerts where customer_id=?",Integer.class,prefix));
        assertEquals(2,((Number)queries.alert(id,0,50).get("evidenceCount")).intValue());
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("delete from audit_events where entity_type='ALERT' and entity_id=?",Long.toString(id)));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("delete from alerts where id=?",id));
        assertFalse(queries.customers(0,200).toString().contains("Synthetic"));
    }
    @Test void lateDepositDetectsPreviouslyIngestedOutflow() {
        ingestion.transaction(tx("-out","800","OUT",now));
        var result=ingestion.transaction(tx("-in","1000","IN",now.minusSeconds(60)));
        long id=((Number)result.get("alertId")).longValue();
        assertTrue(queries.alert(id,0,50).get("rules").toString().contains("RAPID_MOVEMENT"));
    }
    @Test void caseAndAlertDispositionAreAuditedAndTerminal() {
        long alert=((Number)ingestion.transaction(tx("-1","10000","OUT",now)).get("alertId")).longValue();
        long caseId=((Number)workflow.createCase(new CaseInput("Synthetic case",List.of(alert),actor),actor).get("id")).longValue();
        workflow.disposition(alert,new Disposition("IN_REVIEW","Review started"),actor);
        workflow.disposition(alert,new Disposition("CLEARED","Synthetic activity verified"),actor);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->workflow.disposition(alert,new Disposition("ESCALATED","Late change"),actor));
        workflow.updateCase(caseId,new CaseUpdate("CLOSED","Review complete",actor),actor);
        assertEquals(2,queries.audit("CASE",caseId,0,50).size());
        assertEquals(actor,queries.alert(alert,0,50).get("analyst"));
        assertNull(ingestion.transaction(tx("-late-small","1","OUT",now.minusSeconds(30))).get("alertId"));
    }
    @Test void concurrentIngestionCreatesOneActiveAlert() throws Exception {
        ExecutorService executor=Executors.newFixedThreadPool(4);
        try {
            List<Callable<Map<String,Object>>> work=new ArrayList<>();
            for(int i=0;i<8;i++) {int n=i; work.add(()->ingestion.transaction(tx("-"+n,"10000","OUT",now.plusSeconds(n))));}
            for(var future:executor.invokeAll(work)) assertNotNull(future.get().get("alertId"));
            assertEquals(1,jdbc.queryForObject("select count(*) from alerts where customer_id=?",Integer.class,prefix));
            assertEquals(8,jdbc.queryForObject("select count(*) from transactions where account_id=?",Integer.class,prefix));
        } finally {executor.shutdownNow();}
    }
    @Test void batchRollbackAndPartialTransactionResults() {
        String id=prefix+"-new";
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->batch.customers(List.of(customer(id),customer(prefix))));
        assertEquals(0,jdbc.queryForObject("select count(*) from customers where id=?",Integer.class,id));
        var invalid=new TransactionInput(prefix+"-bad","missing",BigDecimal.ONE,"USD","IN","synthetic","BANK",now,"IN");
        var result=batch.transactions(List.of(tx("-ok","1","IN",now),invalid));
        assertEquals(1,result.get("accepted"));assertEquals(1,result.get("rejected"));
    }
    @Test void httpSecurityMasksListsRejectsUnauthorizedAndDocumentsApi() throws Exception {
        HttpClient client=HttpClient.newHttpClient();
        assertEquals(401,request(client,"/api/v1/customers",null).statusCode());
        var user=org.springframework.security.core.userdetails.User.withUsername(actor).password("unused").roles("USER").build();
        String token=tokens.generateAccessToken(user);
        assertEquals(200,request(client,"/api/v1/customers",token).statusCode());
        assertEquals(403,request(client,"/api/v1/customers/"+prefix,token).statusCode());
        assertEquals(403,request(client,"/api/v1/config/rules",token).statusCode());
        assertEquals(401,request(client,"/api/v1/customers",tokens.generateRefreshToken(user)).statusCode());
        var analyst=org.springframework.security.core.userdetails.User.withUsername(actor).password("unused").roles("ANALYST").build();
        var detail=request(client,"/api/v1/customers/"+prefix,tokens.generateAccessToken(analyst));
        assertEquals(200,detail.statusCode());assertTrue(detail.body().contains("Synthetic"));
        assertEquals(200,request(client,"/v3/api-docs",null).statusCode());
    }
    @Test void authenticationUsesAccessAndRefreshTokensWithoutPersistingAccessTokens() throws Exception {
        HttpClient client=HttpClient.newHttpClient();
        String username="u"+prefix.replace("-","").substring(0,15);
        String password="synthetic-password";
        String registerBody="""
            {"username":"%s","email":"%s@example.test","password":"%s"}
            """.formatted(username,username,password);
        var register=post(client,"/api/v1/auth/register",registerBody,null,null);
        assertEquals(201,register.statusCode(),register.body());

        String loginBody="""
            {"username":"%s","password":"%s"}
            """.formatted(username,password);
        var login=post(client,"/api/v1/auth/login",loginBody,null,null);
        assertEquals(200,login.statusCode(),login.body());
        var tokensJson=objectMapper.readTree(login.body());
        String accessToken=tokensJson.get("accessToken").asText();
        String refreshToken=tokensJson.get("refreshToken").asText();
        assertEquals(200,request(client,"/api/v1/customers",accessToken).statusCode());
        assertEquals(401,request(client,"/api/v1/customers",refreshToken).statusCode());

        var refreshed=post(client,"/api/v1/auth/refresh","",null,Map.of("refresh_token","Bearer "+refreshToken));
        assertEquals(200,refreshed.statusCode(),refreshed.body());
        assertFalse(objectMapper.readTree(refreshed.body()).get("accessToken").asText().isBlank());
        assertEquals(0,jdbc.queryForObject("""
            select count(*) from information_schema.columns
            where table_schema=current_schema() and table_name='users' and column_name='access_token'
            """,Integer.class));
    }
    @Test void csvImportsQuotedValuesAndRejectsMalformedFormats() {
        String csv="customer_id,first_name,last_name,country,customer_since,kyc_status,risk_rating,is_politically_exposed\n"+prefix+"-csv,\"Synthetic, Test\",Person,IN,2020-01-01,VERIFIED,LOW,0\n";
        var file=new org.springframework.mock.web.MockMultipartFile("file","customers.csv","text/csv",csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var parsed=batch.csv("customers",file);
        assertEquals("Synthetic, Test",((CustomerInput)((List<?>)parsed.get("customers")).get(0)).firstName());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->batch.csv("customers",new org.springframework.mock.web.MockMultipartFile("file",csv.replace("2020-01-01","bad-date").getBytes())));
    }

    @Test void httpWritesValidateInputsAndIngestStreamingTransactions() throws Exception {
        HttpClient client=HttpClient.newHttpClient();
        var admin=org.springframework.security.core.userdetails.User.withUsername(actor).password("unused").roles("ADMIN").build();
        String token=tokens.generateAccessToken(admin);
        String body="""
            {"id":"%s-http","accountId":"%s","amount":10000,"currency":"USD","direction":"OUT",
             "counterparty":"SYNTHETIC","channel":"BANK","occurredAt":"%s","jurisdiction":"IN"}
            """.formatted(prefix,prefix,now);
        var uri=URI.create("http://localhost:"+port+"/api/v1/transactions");
        var valid=HttpRequest.newBuilder(uri).header("Authorization","Bearer "+token).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var result=client.send(valid,HttpResponse.BodyHandlers.ofString());
        assertEquals(201,result.statusCode(),result.body());assertTrue(result.body().contains("alertId"));
        assertEquals(200,client.send(valid,HttpResponse.BodyHandlers.ofString()).statusCode());
        var invalid=HttpRequest.newBuilder(uri).header("Authorization","Bearer "+token).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.replace("10000","-1"))).build();
        var error=client.send(invalid,HttpResponse.BodyHandlers.ofString());
        assertEquals(400,error.statusCode(),error.body());assertTrue(error.body().contains("timestamp"));
        assertEquals(400,request(client,"/api/v1/alerts?size=999",token).statusCode());
        assertEquals(404,request(client,"/api/v1/accounts/missing",token).statusCode());
    }
    @Test void configurationChangesApplyAndRatesRemainSnapshotted() {
        String party="SYNTHETIC-"+prefix;
        configuration.watch(new WatchInput("COUNTERPARTY",party,true),actor);
        var input=new TransactionInput(prefix+"-watch",prefix,new BigDecimal("0.01"),"USD","OUT",party.toLowerCase(Locale.ROOT),"BANK",now,"IN");
        assertNotNull(ingestion.transaction(input).get("alertId"));
        configuration.rate("ZZZ",new RateInput(new BigDecimal("2")),actor);
        var foreign=new TransactionInput(prefix+"-fx",prefix,new BigDecimal("100"),"ZZZ","OUT","SYNTHETIC","BANK",now,"IN");
        ingestion.transaction(foreign);configuration.rate("ZZZ",new RateInput(new BigDecimal("3")),actor);
        assertEquals(new BigDecimal("200.00"),queries.transaction(foreign.id()).get("base_amount"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->configuration.rule("HIGH_RISK",new RuleInput(false,BigDecimal.ONE,BigDecimal.ONE,24,1,60),actor));
    }

    @Test @SuppressWarnings("unchecked")
    void bundledSyntheticCsvDemonstratesThreeTypologies() throws Exception {
        configuration.watch(new WatchInput("COUNTERPARTY","SYNTHETIC WATCHLIST PARTY",true),actor);
        Map<String,Object> result=null;
        for(String kind:List.of("customers","accounts","transactions")) {
            String csv=java.nio.file.Files.readString(java.nio.file.Path.of("data",kind+".csv")).replace("DEMO_",prefix+"-");
            var file=new org.springframework.mock.web.MockMultipartFile("file",kind+".csv","text/csv",csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var parsed=batch.csv(kind,file);
            result=switch(kind) {
                case "customers" -> batch.customers((List<CustomerInput>)parsed.get(kind));
                case "accounts" -> batch.accounts((List<AccountInput>)parsed.get(kind));
                default -> batch.transactions((List<TransactionInput>)parsed.get(kind));
            };
        }
        assertEquals(6,result.get("accepted"));assertEquals(0,result.get("rejected"));
        var rules=jdbc.queryForList("select r.rule_code from alert_rules r join alerts a on a.id=r.alert_id where a.customer_id like ?",String.class,prefix+"-C%");
        assertTrue(rules.containsAll(List.of("STRUCTURING","RAPID_MOVEMENT","HIGH_RISK")),rules.toString());
    }
    private HttpResponse<String> request(HttpClient client,String path,String token) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path));if(token!=null)builder.header("Authorization","Bearer "+token);
        return client.send(builder.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(HttpClient client,String path,String body,String token,Map<String,String> headers) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
            .header("Content-Type","application/json");
        if(token!=null) builder.header("Authorization","Bearer "+token);
        if(headers!=null) headers.forEach(builder::header);
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
