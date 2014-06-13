/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.mifosplatform.portfolio.charge.data;

import static org.mifosplatform.portfolio.savings.SavingsApiConstants.amountParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.bankNumberParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.chargeIdParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.chargesParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.checkNumberParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.paymentTypeIdParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.receiptNumberParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.routingCodeParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.transactionAccountNumberParamName;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.mifosplatform.infrastructure.core.data.DataValidatorBuilder;
import org.mifosplatform.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@Component
public class PaymentTypeChargeDataValidator {

    private final FromJsonHelper fromApiJsonHelper;
    

    @Autowired
    public PaymentTypeChargeDataValidator(final FromJsonHelper fromApiJsonHelper) {
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    public void validateLinkedChargesExternalChargeAmount(final JsonElement element, final DataValidatorBuilder baseDataValidator) {

        if (element.isJsonObject()) {

            final JsonObject topLevelJsonElement = element.getAsJsonObject();

            if (topLevelJsonElement.has(chargesParamName) && topLevelJsonElement.get(chargesParamName).isJsonArray()) {

                final Locale locale = this.fromApiJsonHelper.extractLocaleParameter(topLevelJsonElement);
                final JsonArray array = topLevelJsonElement.get(chargesParamName).getAsJsonArray();

                for (JsonElement jsonElement : array) {
                    final JsonObject linkedChargeElement = jsonElement.getAsJsonObject();

                    final Long chargeId = this.fromApiJsonHelper.extractLongNamed(chargeIdParamName, linkedChargeElement);
                    baseDataValidator.reset().parameter(chargeIdParamName).value(chargeId).longGreaterThanZero();

                    final BigDecimal amount = this.fromApiJsonHelper.extractBigDecimalNamed(amountParamName, linkedChargeElement, locale);
                    baseDataValidator.reset().parameter(amountParamName).value(amount).notNull().positiveAmount();
                }

            }

        }

    }
    
}