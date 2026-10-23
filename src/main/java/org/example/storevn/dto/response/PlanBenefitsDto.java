package org.example.storevn.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlanBenefitsDto {

    @Builder.Default
    List<PlanBonusDto> bonuses = List.of();

    @Builder.Default
    List<PlanAppQuotaDto> appQuotas = List.of();

}
