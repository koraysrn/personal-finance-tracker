package com.personal.finance.transaction;

import com.personal.finance.account.Account;
import com.personal.finance.category.Category;
import com.personal.finance.category.Tag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * A single money movement on one account. The amount is always stored positive;
 * its effect on the balance is derived from {@link #type}.
 */
@Entity
@Table(name = "transaction_tx")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_RECURRING = "RECURRING";
    public static final String SOURCE_IMPORT = "IMPORT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionType type;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "tx_date", nullable = false)
    private LocalDate date;

    @Column(length = 1024)
    private String note;

    @Column(name = "linked_tx_id")
    private Long linkedTxId;

    @Column(nullable = false, length = 16)
    private String source = SOURCE_MANUAL;

    @Column(name = "idempotency_key", length = 64, unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @ManyToMany
    @JoinTable(
            name = "transaction_tags",
            joinColumns = @JoinColumn(name = "transaction_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();
}
