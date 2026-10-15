package org.example.storevn.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

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
}
