package vn.antam.clinic.service.pharmacy;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Business contract; implemented by the Spring-managed PharmacyAlertServiceImpl. */
public interface PharmacyAlertService {
    public record Batch(
        long id,
        long counterId,
        long medicationId,
        String name,
        String unit,
        String batchCode,
        LocalDate expiryDate,
        BigDecimal quantity,
        BigDecimal available
    ) {}

    public record LowStock(
        long medicationId,
        long counterId,
        String name,
        String unit,
        BigDecimal available,
        int reorderLevel
    ) {}

    public record Alerts(
        List<Batch> expired,
        List<Batch> nearExpiry,
        List<LowStock> lowStock,
        int total,
        LocalDate asOf,
        int nearExpiryDays
    ) {}

    public Alerts alerts(Long counterId);
}
