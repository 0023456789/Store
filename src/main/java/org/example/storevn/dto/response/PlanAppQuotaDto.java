package org.example.storevn.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlanAppQuotaDto {
    Long appId;
    String appCode;
    String appName;
    String quotaType;
    Long quotaMb;
}
