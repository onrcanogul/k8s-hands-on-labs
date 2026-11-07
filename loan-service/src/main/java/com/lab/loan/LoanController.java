package com.lab.loan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@RestController
@RequestMapping("/loans")
public class LoanController {

    private static final Logger log = LoggerFactory.getLogger(LoanController.class);

    record Customer(long id, String name, int creditScore, BigDecimal monthlyIncome, String servedBy) {}

    record LoanOffer(long customerId, String customerName, boolean approved, String reason,
                     BigDecimal amount, int termMonths, BigDecimal monthlyRatePercent,
                     BigDecimal monthlyInstallment, String loanPod, String customerPod) {}

    private final RestClient customerClient;

    @Value("${HOSTNAME:local}")
    private String hostname;

    public LoanController(RestClient customerClient) {
        this.customerClient = customerClient;
    }

    // Example: GET /loans/offer?customerId=1&amount=50000&termMonths=12
    @GetMapping("/offer")
    public ResponseEntity<?> offer(@RequestParam long customerId,
                                   @RequestParam BigDecimal amount,
                                   @RequestParam(defaultValue = "12") int termMonths) {
        log.info("Offer request customerId={} amount={} term={} (pod={})", customerId, amount, termMonths, hostname);

        Customer customer;
        try {
            // Synchronous call: this thread blocks until customer-service responds.
            customer = customerClient.get()
                    .uri("/customers/{id}", customerId)
                    .retrieve()
                    .body(Customer.class);
        } catch (HttpClientErrorException.NotFound e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Customer not found: " + customerId));
        } catch (ResourceAccessException e) {
            // Connection refused, DNS failure and timeouts end up here.
            log.error("customer-service unreachable: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "customer-service unreachable", "detail", String.valueOf(e.getMessage())));
        }

        return ResponseEntity.ok(evaluate(customer, amount, termMonths));
    }

    private LoanOffer evaluate(Customer c, BigDecimal amount, int termMonths) {
        if (c.creditScore() < 1000) {
            return new LoanOffer(c.id(), c.name(), false, "Credit score too low",
                    amount, termMonths, null, null, hostname, c.servedBy());
        }

        // Monthly interest rate (%) by score: higher score -> lower rate
        BigDecimal ratePercent = c.creditScore() >= 1500 ? new BigDecimal("2.90") : new BigDecimal("3.90");        BigDecimal installment = annuity(amount, ratePercent, termMonths);

        // Installment must not exceed 50% of monthly income
        BigDecimal maxInstallment = c.monthlyIncome().multiply(new BigDecimal("0.5"));
        if (installment.compareTo(maxInstallment) > 0) {
            return new LoanOffer(c.id(), c.name(), false, "Installment exceeds 50% of income",
                    amount, termMonths, ratePercent, installment, hostname, c.servedBy());
        }

        return new LoanOffer(c.id(), c.name(), true, "Approved",
                amount, termMonths, ratePercent, installment, hostname, c.servedBy());
    }

    // Equal installments (annuity): P * r / (1 - (1+r)^-n)
    private static BigDecimal annuity(BigDecimal principal, BigDecimal ratePercent, int n) {
        double r = ratePercent.doubleValue() / 100.0;
        double p = principal.doubleValue();
        double payment = p * r / (1 - Math.pow(1 + r, -n));
        return BigDecimal.valueOf(payment).setScale(2, RoundingMode.HALF_UP);
    }
}
