package com.example.detection.service;

import com.example.detection.dto.AmlRequests.*;
import com.example.detection.model.*;
import com.example.detection.repository.*;
import jakarta.validation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IngestionService {
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final FinancialTransactionRepository transactions;
    private final MonitoringRepository monitoring;
    private final AlertService alerts;
    private final DetectionEngine engine;
    private final Validator validator;
    private final jakarta.persistence.EntityManager entityManager;

    @Transactional
    public Customer customer(CustomerInput input) {
        validate(input);
        if (customers.existsById(input.id())) throw conflict("Customer ID already exists");
        Customer entity = new Customer();
        copyRecord(input,entity);
        entityManager.persist(entity); entityManager.flush(); return entity;
    }
    @Transactional
    public Account account(AccountInput input) {
        validate(input);
        if (input.closeDate()!=null && input.closeDate().isBefore(input.openDate())) throw bad("closeDate precedes openDate");
        if (!monitoring.rates().containsKey(input.currency())) throw bad("Configure the account currency exchange rate first");
        if (accounts.existsById(input.id())) throw conflict("Account ID already exists");
        Customer owner = customers.findById(input.customerId()).orElseThrow(()->missing("Customer"));
        Account entity = new Account(); copyRecord(input,entity); entity.setCustomer(owner);
        entityManager.persist(entity); entityManager.flush(); return entity;
    }
    @Transactional
    public Map<String,Object> transaction(TransactionInput input) {
        validate(input);
        Account account = accounts.findById(input.accountId()).orElseThrow(()->missing("Account"));
        // A database row lock serializes all accounts belonging to this customer across application instances.
        Customer customer = customers.lockById(account.getCustomer().getId()).orElseThrow(()->missing("Customer"));
        Optional<FinancialTransaction> previous = transactions.findById(input.id());
        if (previous.isPresent()) {
            FinancialTransaction old = previous.get();
            if (!same(old,input)) throw conflict("Transaction ID was already used with a different payload");
            return Map.of("id",old.getId(),"duplicate",true);
        }
        LocalDate date = input.occurredAt().atZone(ZoneOffset.UTC).toLocalDate();
        if (date.isBefore(account.getOpenDate()) || (account.getCloseDate()!=null && date.isAfter(account.getCloseDate())))
            throw bad("Transaction date falls outside the account lifetime");
        Map<String,BigDecimal> rates = monitoring.rates();
        BigDecimal rate=rates.get(input.currency()), usd=rates.get("USD");
        if(rate==null || usd==null) throw bad("Missing currency or USD exchange rate");
        FinancialTransaction entity = new FinancialTransaction(); copyRecord(input,entity);
        entity.setAccount(account); entity.setOccurredAt(input.occurredAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)); entity.setExchangeRate(rate);
        entity.setBaseAmount(input.amount().multiply(rate).setScale(2,RoundingMode.HALF_UP));
        entity.setUsdAmount(entity.getBaseAmount().divide(usd,2,RoundingMode.HALF_UP));
        if(entity.getBaseAmount().signum()==0 || entity.getUsdAmount().signum()==0) throw bad("Amount is below the normalized currency precision");
        entity.setIngestedAt(Instant.now()); entityManager.persist(entity); entityManager.flush();
        var rules=monitoring.rules(); var watch=monitoring.watchlist();
        // Late events also reevaluate later anchors whose rolling windows/baselines they can change.
        Instant eventTime=entity.getOccurredAt();
        Instant start=eventTime.atZone(ZoneOffset.UTC).toLocalDate().minusDays(90).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end=eventTime.plus(Duration.ofDays(91));
        var history=monitoring.history(customer.getId(),start,end);
        List<DetectionEngine.Hit> hits=new ArrayList<>();
        for(var anchor:history) if(!anchor.time().isBefore(eventTime)) {
            // Only changed patterns involving this new event may create/extend alerts.
            // Replaying later anchors must not resurrect an unrelated disposed pattern.
            hits.addAll(engine.evaluate(anchor,history,rules,watch).stream()
                .filter(hit -> hit.evidence().contains(entity.getId())).toList());
        }
        Long alertId=alerts.aggregate(customer,hits);
        Map<String,Object> result=new LinkedHashMap<>(); result.put("id",entity.getId()); result.put("duplicate",false);
        result.put("baseAmount",entity.getBaseAmount()); result.put("alertId",alertId); return result;
    }
    private boolean same(FinancialTransaction t, TransactionInput i) {
        return t.getAccount().getId().equals(i.accountId()) && t.getAmount().compareTo(i.amount())==0 && t.getCurrency().equals(i.currency())
            && t.getDirection().equals(i.direction()) && t.getCounterparty().equals(i.counterparty()) && t.getChannel().equals(i.channel())
            && t.getOccurredAt().equals(i.occurredAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)) && t.getJurisdiction().equals(i.jurisdiction());
    }
    private <T> void validate(T input) { var errors=validator.validate(input); if(!errors.isEmpty()) throw new ConstraintViolationException(errors); }
    // Records have component accessors rather than JavaBean getters. Copy only explicitly declared input fields.
    private void copyRecord(Object input,Object target) {
        for(var field:input.getClass().getRecordComponents()) {
            var property=BeanUtils.getPropertyDescriptor(target.getClass(),field.getName());
            if(property!=null && property.getWriteMethod()!=null) try {
                property.getWriteMethod().invoke(target,field.getAccessor().invoke(input));
            } catch(ReflectiveOperationException ex) { throw new IllegalStateException("Cannot map input",ex); }
        }
    }
    public static ResponseStatusException missing(String entity) { return new ResponseStatusException(HttpStatus.NOT_FOUND,entity+" not found"); }
    public static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    public static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
