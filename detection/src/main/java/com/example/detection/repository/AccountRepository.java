package com.example.detection.repository;
import com.example.detection.model.Account;
import org.springframework.data.jpa.repository.*;
public interface AccountRepository extends JpaRepository<Account, String> {}
