package org.example.storevn.service;

import org.example.storevn.entity.PaymentLog;
import org.example.storevn.enums.PaymentEventSource;
import org.example.storevn.repository.PaymentLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentLogServiceTest {

    @Mock
    private PaymentLogRepository repository;

    private PaymentLogService service;

    @BeforeEach
    void setUp() {
        service = new PaymentLogService(repository);
    }

    @Test
    void log_persistsAllPaymentEventFields() {
        when(repository.save(any(PaymentLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentLog result = service.log(PaymentEventSource.IPN, "TXN-1", "VNP-1", 89000L,
                "00", "NCB", true, "00", "{\"vnp_TxnRef\":\"TXN-1\"}");

        ArgumentCaptor<PaymentLog> captor = ArgumentCaptor.forClass(PaymentLog.class);
        verify(repository).save(captor.capture());
        assertThat(result).isSameAs(captor.getValue());
        assertThat(result.getSource()).isEqualTo(PaymentEventSource.IPN);
        assertThat(result.getTxnRef()).isEqualTo("TXN-1");
        assertThat(result.getVnpTransactionNo()).isEqualTo("VNP-1");
        assertThat(result.getAmount()).isEqualTo(89000L);
        assertThat(result.getVnpResponseCode()).isEqualTo("00");
        assertThat(result.getVnpBankCode()).isEqualTo("NCB");
        assertThat(result.getSignatureValid()).isTrue();
        assertThat(result.getRspCode()).isEqualTo("00");
        assertThat(result.getResponseMessage()).isNull();
        assertThat(result.getRawPayload()).contains("TXN-1");
    }

    @Test
    void logIndependent_returnsNullWhenAuditInsertFails() {
        when(repository.save(any(PaymentLog.class))).thenThrow(new RuntimeException("database unavailable"));

        PaymentLog result = service.logIndependent(PaymentEventSource.IPN, "TXN-2", null,
                null, null, null, false, "97", "Invalid Checksum", "{}");

        assertThat(result).isNull();
        verify(repository).save(any(PaymentLog.class));
    }
}
