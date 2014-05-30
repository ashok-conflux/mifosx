/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.mifosplatform.portfolio.charge.data;

import java.math.BigDecimal;

import org.mifosplatform.infrastructure.codes.data.CodeValueData;
import org.mifosplatform.infrastructure.core.data.EnumOptionData;

/**
 * Immutable data object for payment type charge data.
 */
public class PaymentTypeChargeData {

    @SuppressWarnings("unused")
    private final Long id;
    @SuppressWarnings("unused")
    private final Long chargeId;
    @SuppressWarnings("unused")
    private final CodeValueData paymentType;
    @SuppressWarnings("unused")
    private final EnumOptionData chargeCalculationType;
    @SuppressWarnings("unused")
    private final BigDecimal amount;

    public static PaymentTypeChargeData instance(final Long id, final Long chargeId, final CodeValueData paymentType,
            final EnumOptionData chargeCalculationType, final BigDecimal amount) {

        return new PaymentTypeChargeData(id, chargeId, paymentType, chargeCalculationType, amount);
    }

    private PaymentTypeChargeData(final Long id, final Long chargeId, final CodeValueData paymentType, final EnumOptionData chargeCalculationType,
            final BigDecimal amount) {
        this.id = id;
        this.chargeId = chargeId;
        this.paymentType = paymentType;
        this.chargeCalculationType = chargeCalculationType;
        this.amount = amount;
    }

}