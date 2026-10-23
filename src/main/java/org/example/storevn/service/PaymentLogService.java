package org.example.storevn.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.storevn.entity.PaymentLog;
import org.example.storevn.enums.PaymentEventSource;
import org.example.storevn.repository.PaymentLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentLogService {

    private final PaymentLogRepository paymentLogRepository;

    /** Ghi log bằng transaction lưu ngắn của repository khi không có transaction ngoài bao quanh. */
    public PaymentLog log(PaymentEventSource source,
                          String txnRef,
                          String vnpTxnNo,
                          Long amount,
                          String vnpResponseCode,
                          String vnpBankCode,
                          boolean signatureValid,
                          String rspCode,
                          String rawPayload) {
        return persist(source, txnRef, vnpTxnNo, amount, vnpResponseCode, vnpBankCode,
                signatureValid, rspCode, null, rawPayload);
    }

    /** Commit log IPN độc lập để vẫn lưu dấu vết callback nếu transaction cập nhật thanh toán rollback. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentLog logIndependent(PaymentEventSource source,
                                    String txnRef,
                                    String vnpTxnNo,
                                    Long amount,
                                    String vnpResponseCode,
                                    String vnpBankCode,
                                    boolean signatureValid,
                                    String rspCode,
                                    String responseMessage,
                                    String rawPayload) {
        return persist(source, txnRef, vnpTxnNo, amount, vnpResponseCode, vnpBankCode,
                signatureValid, rspCode, responseMessage, rawPayload);
    }

    private PaymentLog persist(PaymentEventSource source,
                               String txnRef,
                               String vnpTxnNo,
                               Long amount,
                               String vnpResponseCode,
                               String vnpBankCode,
                               boolean signatureValid,
                               String rspCode,
                               String responseMessage,
                               String rawPayload) {
        try {
            PaymentLog paymentLog = PaymentLog.builder()
                    .source(source)
                    .txnRef(txnRef)
                    .vnpTransactionNo(vnpTxnNo)
                    .amount(amount)
                    .vnpResponseCode(vnpResponseCode)
                    .vnpBankCode(vnpBankCode)
                    .signatureValid(signatureValid)
                    .rspCode(rspCode)
                    .responseMessage(responseMessage)
                    .rawPayload(rawPayload)
                    .build();
            return paymentLogRepository.save(paymentLog);
        } catch (Exception ex) {
            log.error("Failed to write payment log for txnRef {}: {}", txnRef, ex.getMessage(), ex);
            return null;
        }
    }
}
