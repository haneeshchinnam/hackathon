package com.example.detection.repository;
import com.example.detection.model.Customer;
import org.springframework.data.jpa.repository.*;
public interface CustomerRepository extends JpaRepository<Customer, String> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    java.util.Optional<Customer> lockById(@org.springframework.data.repository.query.Param("id") String id);
}
