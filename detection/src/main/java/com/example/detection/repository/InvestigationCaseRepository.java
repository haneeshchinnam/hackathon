package com.example.detection.repository;
import com.example.detection.model.InvestigationCase;
import org.springframework.data.jpa.repository.*;
public interface InvestigationCaseRepository extends JpaRepository<InvestigationCase, Long> {}
