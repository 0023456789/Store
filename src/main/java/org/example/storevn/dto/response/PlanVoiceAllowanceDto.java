package org.example.storevn.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlanVoiceAllowanceDto {
    Long networkId;
    String networkCode;
    String networkName;
    Integer minutes;
}
