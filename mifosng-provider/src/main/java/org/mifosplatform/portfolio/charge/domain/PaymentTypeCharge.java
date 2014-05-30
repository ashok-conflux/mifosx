package org.mifosplatform.portfolio.charge.domain;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.mifosplatform.infrastructure.codes.domain.CodeValue;
import org.mifosplatform.infrastructure.core.api.JsonCommand;
import org.mifosplatform.infrastructure.core.data.DataValidatorBuilder;
import org.springframework.data.jpa.domain.AbstractPersistable;

@Entity
@Table(name = "m_payment_type_charge", uniqueConstraints = { @UniqueConstraint(columnNames = { "charge_id", "payment_type_id" }, name = "unique_payment_type_charge") })
public class PaymentTypeCharge extends AbstractPersistable<Long> {

    @ManyToOne
    @JoinColumn(name = "payment_type_id", nullable = false)
    private CodeValue paymentType;

    @ManyToOne
    @JoinColumn(name = "charge_id", nullable = false)
    private Charge charge;

    @Column(name = "charge_calculation_type_enum", nullable = false)
    private Integer chargeCalculationType;

    @Column(name = "amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal amount;

    protected PaymentTypeCharge() {}

    public Map<String, Object> update(final JsonCommand command, final DataValidatorBuilder baseDataValidator, final String localeAsInput) {
        final Map<String, Object> actualChanges = new LinkedHashMap<String, Object>(2);

        final String amountParamName = "amount";
        if (command.isChangeInBigDecimalParameterNamed(amountParamName, this.amount)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed(amountParamName);
            actualChanges.put(amountParamName, newValue);
            actualChanges.put("locale", localeAsInput);
            this.amount = newValue;
        }

        final String chargeCalculationParamName = "chargeCalculationType";
        if (command.isChangeInIntegerParameterNamed(chargeCalculationParamName, this.chargeCalculationType)) {
            final Integer newValue = command.integerValueOfParameterNamed(chargeCalculationParamName);
            actualChanges.put(chargeCalculationParamName, newValue);
            actualChanges.put("locale", localeAsInput);
            this.chargeCalculationType = newValue;

            if (charge.isSavingsCharge()) {
                if (!charge.isAllowedSavingsChargeCalculationType()) {
                    baseDataValidator.reset().parameter("chargeCalculationType").value(this.chargeCalculationType)
                            .failWithCodeNoParameterAddedToErrorCode("not.allowed.charge.calculation.type.for.savings");
                }

                if (!ChargeTimeType.fromInt(charge.getChargeTime()).isWithdrawalFee()
                        && ChargeCalculationType.fromInt(charge.getChargeCalculation()).isPercentageOfAmount()) {
                    baseDataValidator.reset().parameter("chargeCalculationType").value(this.chargeCalculationType)
                            .failWithCodeNoParameterAddedToErrorCode("charge.calculation.type.percentage.allowed.only.for.withdrawal");
                }
            }
        }

        return actualChanges;
    }

    public static PaymentTypeCharge create(final CodeValue paymentType, final Charge charge, final Integer chargeCalculationType,
            final BigDecimal amount) {
        return new PaymentTypeCharge(paymentType, charge, chargeCalculationType, amount);
    }

    private PaymentTypeCharge(final CodeValue paymentType, final Charge charge, final Integer chargeCalculationType, final BigDecimal amount) {
        this.charge = charge;
        this.paymentType = paymentType;
        this.chargeCalculationType = chargeCalculationType;
        this.amount = amount;
    }

    public CodeValue paymentType() {
        return this.paymentType;
    }

    public Charge charge() {
        return this.charge;
    }

    public BigDecimal amount() {
        return this.amount;
    }

    public ChargeCalculationType chargeCalculationType() {
        return ChargeCalculationType.fromInt(this.chargeCalculationType);
    }
}