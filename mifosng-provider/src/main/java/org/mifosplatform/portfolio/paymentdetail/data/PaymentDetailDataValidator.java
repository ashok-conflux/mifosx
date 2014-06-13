/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.mifosplatform.portfolio.paymentdetail.data;

import static org.mifosplatform.portfolio.savings.SavingsApiConstants.bankNumberParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.checkNumberParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.paymentTypeIdParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.receiptNumberParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.routingCodeParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.transactionAccountNumberParamName;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.mifosplatform.infrastructure.core.data.DataValidatorBuilder;
import org.mifosplatform.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.JsonElement;

@Component
public class PaymentDetailDataValidator {

    private final FromJsonHelper fromApiJsonHelper;
    

    @Autowired
    public PaymentDetailDataValidator(final FromJsonHelper fromApiJsonHelper) {
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    public void validatePaymentDetails(final JsonElement element, final DataValidatorBuilder baseDataValidator) {
        // Validate all string payment detail fields for max length
        final Integer paymentTypeId = this.fromApiJsonHelper.extractIntegerWithLocaleNamed(paymentTypeIdParamName, element);
        baseDataValidator.reset().parameter(paymentTypeIdParamName).value(paymentTypeId).ignoreIfNull().integerGreaterThanZero();
        final Set<String> paymentDetailParameters = new HashSet<String>(Arrays.asList(transactionAccountNumberParamName,
                checkNumberParamName, routingCodeParamName, receiptNumberParamName, bankNumberParamName));
        for (final String paymentDetailParameterName : paymentDetailParameters) {
            final String paymentDetailParameterValue = this.fromApiJsonHelper.extractStringNamed(paymentDetailParameterName, element);
            baseDataValidator.reset().parameter(paymentDetailParameterName).value(paymentDetailParameterValue).ignoreIfNull()
                    .notExceedingLengthOf(50);
        }
    }
    
}