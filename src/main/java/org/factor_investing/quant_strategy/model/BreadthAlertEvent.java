package org.factor_investing.quant_strategy.model;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "breadth_alert_event")
@Getter
@Setter
public class BreadthAlertEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "alert_type", nullable = false)
    private String alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NiftyIndexName universe;

    @Column(name = "trigger_date", nullable = false)
    private LocalDate triggerDate;

    @Type(JsonType.class)
    @Column(name = "trigger_values", nullable = false, columnDefinition = "jsonb")
    private Map<String, Double> triggerValues;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_state", nullable = false)
    private AlertDeliveryState deliveryState;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "dedup_key", nullable = false, unique = true)
    private String dedupKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
