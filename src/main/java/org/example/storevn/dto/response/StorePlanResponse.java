package org.example.storevn.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StorePlanResponse {
    Long id;
    String code;
    String name;
    String description;
    Long price;
    Integer durationMonths;
    String quotaType;
    Long dataQuotaMb;
    Integer cycleDays;
    Integer voiceMinutes;
    String cutoffPolicy;
    Integer throttleSpeedKbps;

    // ── Nhóm ưu đãi (Benefits) ──
    @Builder.Default
    List<PlanBonusDto> firstCycleBonuses = List.of();

    @Builder.Default
    List<PlanAppQuotaDto> appQuotas = List.of();

    @Builder.Default
    List<PlanVoiceAllowanceDto> voiceAllowances = List.of();

    PlanBenefitsDto benefits;
}

