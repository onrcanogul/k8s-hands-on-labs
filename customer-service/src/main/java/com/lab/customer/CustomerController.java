package com.lab.customer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    public record Customer(long id, String name, int creditScore, BigDecimal monthlyIncome, String servedBy) {}

    private static final Map<Long, Customer> CUSTOMERS = Map.of(
            1L, new Customer(1, "Ayse Yilmaz", 1650, new BigDecimal("85000"), null),
            2L, new Customer(2, "Mehmet Demir", 1100, new BigDecimal("42000"), null),
            3L, new Customer(3, "Zeynep Kaya", 700, new BigDecimal("30000"), null)
    );

    // Shows which pod answered. In Kubernetes, HOSTNAME = pod name.
    @Value("${HOSTNAME:local}")
    private String hostname;

    @GetMapping("/{id}")
    public ResponseEntity<Customer> get(@PathVariable long id) {
        log.info("GET /customers/{} (pod={})", id, hostname);
        Customer c = CUSTOMERS.get(id);
        if (c == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new Customer(c.id(), c.name(), c.creditScore(), c.monthlyIncome(), hostname));
    }
}
