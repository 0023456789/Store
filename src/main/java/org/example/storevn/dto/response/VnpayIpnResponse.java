package org.example.storevn.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VnpayIpnResponse {

    @JsonProperty("RspCode")
    private String rspCode;

    @JsonProperty("Message")
    private String message;

    public static VnpayIpnResponse success() {
        return new VnpayIpnResponse("00", "Confirm Success");
    }

    public static VnpayIpnResponse orderNotFound() {
        return new VnpayIpnResponse("01", "Order not found");
    }

    public static VnpayIpnResponse alreadyConfirmed() {
        return new VnpayIpnResponse("02", "Order already confirmed");
    }

    public static VnpayIpnResponse invalidAmount() {
        return new VnpayIpnResponse("04", "Invalid Amount");
    }

    public static VnpayIpnResponse invalidSignature() {
        return new VnpayIpnResponse("97", "Invalid Checksum");
    }

    public static VnpayIpnResponse unknownError() {
        return new VnpayIpnResponse("99", "Unknown error");
    }
}
