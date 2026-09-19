package com.example.detection.repository;
import com.example.detection.model.Alert;
import org.springframework.data.jpa.repository.*;
public interface AlertRepository extends JpaRepository<Alert, Long> {}
