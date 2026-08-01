package com.settleup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * SettleUp — Group Expense Splitting with Double-Entry Ledger Accounting.
 *
 * Architecture highlights:
 *  - Immutable append-only ledger (no UPDATE / DELETE on ledger_entries ever)
 *  - All money arithmetic uses BigDecimal — never float/double
 *  - Async settlement processing via RabbitMQ
 *  - Balance reads cached in Redis, invalidated on each new ledger write
 */
@SpringBootApplication
@EnableAsync
public class SettleUpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettleUpApplication.class, args);
    }
}
