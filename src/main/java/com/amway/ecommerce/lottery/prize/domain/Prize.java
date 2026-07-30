package com.amway.ecommerce.lottery.prize.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "prize")
public class Prize {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrizeType type;

    /** Win probability in basis of 10000 (萬分比). */
    @Column(nullable = false)
    private int probability;

    @Column(name = "total_stock", nullable = false)
    private int totalStock;

    @Column(name = "remaining_stock", nullable = false)
    private int remainingStock;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Prize() {
    }

    public Prize(Long activityId, String name, PrizeType type, int probability, int totalStock) {
        this.activityId = activityId;
        this.name = name;
        this.type = type;
        this.probability = probability;
        this.totalStock = totalStock;
        this.remainingStock = totalStock;
    }

    public boolean isThanks() {
        return type == PrizeType.THANKS;
    }

    public boolean hasStock() {
        return isThanks() || remainingStock > 0;
    }

    /**
     * Consume one unit of stock. THANKS never runs out. Returns false when a
     * real prize is already sold out so the caller can degrade to THANKS.
     */
    public boolean consumeOne() {
        if (isThanks()) {
            return true;
        }
        if (remainingStock <= 0) {
            return false;
        }
        remainingStock--;
        return true;
    }

    public Long getId() {
        return id;
    }

    public Long getActivityId() {
        return activityId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PrizeType getType() {
        return type;
    }

    public int getProbability() {
        return probability;
    }

    public void setProbability(int probability) {
        this.probability = probability;
    }

    public int getTotalStock() {
        return totalStock;
    }

    public int getRemainingStock() {
        return remainingStock;
    }

    /** Adjust stock when an admin re-configures the prize at runtime. */
    public void adjustStock(int newTotalStock, int newRemainingStock) {
        this.totalStock = newTotalStock;
        this.remainingStock = newRemainingStock;
    }

    public Long getVersion() {
        return version;
    }
}
