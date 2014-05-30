package org.mifosplatform.portfolio.charge.domain;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTypeChargeRepository extends JpaRepository<PaymentTypeCharge, Long>, JpaSpecificationExecutor<PaymentTypeCharge> {
    
    @Query("from PaymentTypeCharge ptc where ptc.paymentType.id = :paymentTypeId and ptc.charge.chargeTime = :chargeTime")
     Collection<PaymentTypeCharge> findByPaymentTypeIdAndChargeChargeTime(@Param("paymentTypeId") Long paymentTypeId, @Param("chargeTime") Integer chargeTime);
}