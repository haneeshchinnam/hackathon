package com.example.detection.repository;
import com.example.detection.model.FinancialTransaction;
import org.springframework.data.jpa.repository.*;
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, String> {}
