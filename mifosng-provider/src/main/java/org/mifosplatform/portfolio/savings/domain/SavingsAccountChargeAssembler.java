/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.mifosplatform.portfolio.savings.domain;

import static org.mifosplatform.portfolio.savings.SavingsApiConstants.SAVINGS_ACCOUNT_RESOURCE_NAME;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.amountParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.chargeCalculationTypeParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.chargeIdParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.chargeTimeTypeParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.chargesParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.dueAsOfDateParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.feeOnMonthDayParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.feeIntervalParamName;
import static org.mifosplatform.portfolio.savings.SavingsApiConstants.idParamName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.joda.time.LocalDate;
import org.joda.time.MonthDay;
import org.mifosplatform.infrastructure.core.api.JsonCommand;
import org.mifosplatform.infrastructure.core.data.ApiParameterError;
import org.mifosplatform.infrastructure.core.data.DataValidatorBuilder;
import org.mifosplatform.infrastructure.core.exception.PlatformApiDataValidationException;
import org.mifosplatform.infrastructure.core.serialization.FromJsonHelper;
import org.mifosplatform.portfolio.charge.domain.Charge;
import org.mifosplatform.portfolio.charge.domain.ChargeCalculationType;
import org.mifosplatform.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.mifosplatform.portfolio.charge.domain.ChargeTimeType;
import org.mifosplatform.portfolio.charge.domain.PaymentTypeCharge;
import org.mifosplatform.portfolio.charge.domain.PaymentTypeChargeRepository;
import org.mifosplatform.portfolio.charge.exception.ChargeCannotBeAppliedToException;
import org.mifosplatform.portfolio.charge.exception.SavingsAccountChargeNotFoundException;
import org.mifosplatform.portfolio.paymentdetail.PaymentDetailConstants;
import org.mifosplatform.portfolio.paymentdetail.domain.PaymentDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@Service
public class SavingsAccountChargeAssembler {

    private final FromJsonHelper fromApiJsonHelper;
    private final ChargeRepositoryWrapper chargeRepository;
    private final SavingsAccountChargeRepository savingsAccountChargeRepository;
    private final PaymentTypeChargeRepository paymentTypeChargeRepository;

    @Autowired
    public SavingsAccountChargeAssembler(final FromJsonHelper fromApiJsonHelper, final ChargeRepositoryWrapper chargeRepository,
            final SavingsAccountChargeRepository savingsAccountChargeRepository,
            final PaymentTypeChargeRepository paymentTypeChargeRepository) {
        this.fromApiJsonHelper = fromApiJsonHelper;
        this.chargeRepository = chargeRepository;
        this.savingsAccountChargeRepository = savingsAccountChargeRepository;
        this.paymentTypeChargeRepository = paymentTypeChargeRepository;
    }

    public Set<SavingsAccountCharge> fromParsedJson(final JsonElement element, final String productCurrencyCode) {

        final Set<SavingsAccountCharge> savingsAccountCharges = new HashSet<SavingsAccountCharge>();

        if (element.isJsonObject()) {
            final JsonObject topLevelJsonElement = element.getAsJsonObject();
            final String dateFormat = this.fromApiJsonHelper.extractDateFormatParameter(topLevelJsonElement);
            final String monthDayFormat = this.fromApiJsonHelper.extractMonthDayFormatParameter(topLevelJsonElement);
            final Locale locale = this.fromApiJsonHelper.extractLocaleParameter(topLevelJsonElement);
            if (topLevelJsonElement.has(chargesParamName) && topLevelJsonElement.get(chargesParamName).isJsonArray()) {
                final JsonArray array = topLevelJsonElement.get(chargesParamName).getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {

                    final JsonObject savingsChargeElement = array.get(i).getAsJsonObject();

                    final Long id = this.fromApiJsonHelper.extractLongNamed(idParamName, savingsChargeElement);
                    final Long chargeId = this.fromApiJsonHelper.extractLongNamed(chargeIdParamName, savingsChargeElement);
                    final BigDecimal amount = this.fromApiJsonHelper.extractBigDecimalNamed(amountParamName, savingsChargeElement, locale);
                    final Integer chargeTimeType = this.fromApiJsonHelper.extractIntegerNamed(chargeTimeTypeParamName,
                            savingsChargeElement, locale);
                    final Integer chargeCalculationType = this.fromApiJsonHelper.extractIntegerNamed(chargeCalculationTypeParamName,
                            savingsChargeElement, locale);
                    final LocalDate dueDate = this.fromApiJsonHelper.extractLocalDateNamed(dueAsOfDateParamName, savingsChargeElement,
                            dateFormat, locale);

                    final MonthDay feeOnMonthDay = this.fromApiJsonHelper.extractMonthDayNamed(feeOnMonthDayParamName,
                            savingsChargeElement, monthDayFormat, locale);
                    final Integer feeInterval = this.fromApiJsonHelper.extractIntegerNamed(feeIntervalParamName, savingsChargeElement,
                            locale);

                    if (id == null) {
                        final Charge chargeDefinition = this.chargeRepository.findOneWithNotFoundDetection(chargeId);

                        if (!chargeDefinition.isSavingsCharge()) {
                            final String errorMessage = "Charge with identifier " + chargeDefinition.getId()
                                    + " cannot be applied to Savings product.";
                            throw new ChargeCannotBeAppliedToException("savings.product", errorMessage, chargeDefinition.getId());
                        }

                        ChargeTimeType chargeTime = null;
                        if (chargeTimeType != null) {
                            chargeTime = ChargeTimeType.fromInt(chargeTimeType);
                        }

                        ChargeCalculationType chargeCalculation = null;
                        if (chargeCalculationType != null) {
                            chargeCalculation = ChargeCalculationType.fromInt(chargeCalculationType);
                        }

                        final boolean status = true;

                        // If charge is linked with payment type then add
                        // separate charge for each payment type.
                        final Set<PaymentTypeCharge> linkedCharges = chargeDefinition.paymentTypeCharges();

                        if (linkedCharges == null || linkedCharges.isEmpty()) {

                            final SavingsAccountCharge savingsAccountCharge = SavingsAccountCharge.createNewWithoutSavingsAccount(
                                    chargeDefinition, amount, chargeTime, chargeCalculation, dueDate, status, feeOnMonthDay, feeInterval);
                            savingsAccountCharges.add(savingsAccountCharge);
                        } else {
                            for (PaymentTypeCharge paymentTypeCharge : linkedCharges) {
                                final SavingsAccount account = null;
                                final SavingsAccountCharge savingsAccountCharge = SavingsAccountCharge.createFromPaymentTypeCharge(account,
                                        paymentTypeCharge);
                                savingsAccountCharges.add(savingsAccountCharge);
                            }
                        }

                    } else {
                        final Long savingsAccountChargeId = id;
                        final SavingsAccountCharge savingsAccountCharge = this.savingsAccountChargeRepository
                                .findOne(savingsAccountChargeId);
                        if (savingsAccountCharge == null) { throw new SavingsAccountChargeNotFoundException(savingsAccountChargeId); }

                        savingsAccountCharge.update(amount, dueDate, feeOnMonthDay, feeInterval);

                        savingsAccountCharges.add(savingsAccountCharge);
                    }
                }
            }
        }

        this.validateSavingsCharges(savingsAccountCharges, productCurrencyCode);
        return savingsAccountCharges;
    }

    public Set<SavingsAccountCharge> fromSavingsProduct(final SavingsProduct savingsProduct) {

        final Set<SavingsAccountCharge> savingsAccountCharges = new HashSet<SavingsAccountCharge>();
        Set<Charge> productCharges = savingsProduct.charges();
        for (Charge charge : productCharges) {
            ChargeTimeType chargeTime = null;
            if (charge.getChargeTime() != null) {
                chargeTime = ChargeTimeType.fromInt(charge.getChargeTime());
            }
            if (chargeTime != null && chargeTime.isOnSpecifiedDueDate()) {
                continue;
            }

            ChargeCalculationType chargeCalculation = null;
            if (charge.getChargeCalculation() != null) {
                chargeCalculation = ChargeCalculationType.fromInt(charge.getChargeCalculation());
            }
            final boolean status = true;
            final SavingsAccountCharge savingsAccountCharge = SavingsAccountCharge.createNewWithoutSavingsAccount(charge,
                    charge.getAmount(), chargeTime, chargeCalculation, null, status, charge.getFeeOnMonthDay(), charge.feeInterval());
            savingsAccountCharges.add(savingsAccountCharge);
        }
        return savingsAccountCharges;
    }

    private void validateSavingsCharges(final Set<SavingsAccountCharge> charges, final String productCurrencyCode) {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(SAVINGS_ACCOUNT_RESOURCE_NAME);
        //boolean isOneWithdrawalPresent = false;
        boolean isOneAnnualPresent = false;
        for (SavingsAccountCharge charge : charges) {
            if (!charge.hasCurrencyCodeOf(productCurrencyCode)) {
                baseDataValidator.reset().parameter("currency").value(charge.getCharge().getId())
                        .failWithCodeNoParameterAddedToErrorCode("currency.and.charge.currency.not.same");
            }

            /*if (charge.isWithdrawalFee()) {
                if (isOneWithdrawalPresent) {
                    baseDataValidator.reset().failWithCodeNoParameterAddedToErrorCode("multiple.withdrawal.fee.per.account.not.supported");
                }
                isOneWithdrawalPresent = true;
            }*/

            if (charge.isAnnualFee()) {
                if (isOneAnnualPresent) {
                    baseDataValidator.reset().failWithCodeNoParameterAddedToErrorCode("multiple.annual.fee.per.account.not.supported");
                }
                isOneAnnualPresent = true;
            }
        }
        if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
    }

    public Set<SavingsAccountCharge> fromLinkedPaymentTypeCharges(final Long paymentTypeId, final ChargeTimeType chargeTimeType) {
        final Set<SavingsAccountCharge> linkedCharges = new HashSet<SavingsAccountCharge>();

        if (paymentTypeId != null) {
            final Collection<PaymentTypeCharge> paymentTypeCharges = this.paymentTypeChargeRepository.findByPaymentTypeIdAndChargeChargeTime(
                    paymentTypeId, chargeTimeType.getValue());
            for (PaymentTypeCharge paymentTypeCharge : paymentTypeCharges) {
                final SavingsAccount savingsAccount = null;
                final SavingsAccountCharge savingsAccountCharge = SavingsAccountCharge.createFromPaymentTypeCharge(savingsAccount, paymentTypeCharge);
                linkedCharges.add(savingsAccountCharge);
            }
        }

        return linkedCharges;
    }

    public Map<Long, BigDecimal> assembleChargesAmount(final JsonCommand command) {
        final JsonElement element = command.parsedJson();
        final Map<Long, BigDecimal> chargeAmounts = new HashMap<Long, BigDecimal>();
        if (element.isJsonObject()) {

            final JsonObject topLevelJsonElement = element.getAsJsonObject();

            if (topLevelJsonElement.has(chargesParamName) && topLevelJsonElement.get(chargesParamName).isJsonArray()) {

                final Locale locale = this.fromApiJsonHelper.extractLocaleParameter(topLevelJsonElement);
                final JsonArray array = topLevelJsonElement.get(chargesParamName).getAsJsonArray();

                for (JsonElement jsonElement : array) {
                    final JsonObject linkedChargeElement = jsonElement.getAsJsonObject();

                    final Long chargeId = this.fromApiJsonHelper.extractLongNamed(chargeIdParamName, linkedChargeElement);
                    final BigDecimal amount = this.fromApiJsonHelper.extractBigDecimalNamed(amountParamName, linkedChargeElement, locale);
                    chargeAmounts.put(chargeId, amount);
                }

            }

        }

        return chargeAmounts;
    }

    public Set<SavingsAccountCharge> fromLinkedChargesAndExternalChargesAmount(final JsonCommand command, final PaymentDetail paymentDetail,
            final ChargeTimeType chargeTimeType) {
        
        if(paymentDetail == null) return null;
        
        final Long paymentTypeId = paymentDetail.getPaymentType().getId(); 
        
        final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(SAVINGS_ACCOUNT_RESOURCE_NAME);
        
        final Set<SavingsAccountCharge> linkedCharges = this.fromLinkedPaymentTypeCharges(paymentTypeId, chargeTimeType);
        final Map<Long, BigDecimal> externalChargesAmount = this.assembleChargesAmount(command);
        final Set<Long> nonLinkedCharges = new HashSet<Long>();        
        boolean isChargeLinked = false;
        if (externalChargesAmount != null && !externalChargesAmount.isEmpty()) {
            for (Map.Entry<Long, BigDecimal> entry : externalChargesAmount.entrySet()) {
                isChargeLinked = false;
                for (SavingsAccountCharge savingsAccountCharge : linkedCharges) {
                    if (entry.getKey().equals(savingsAccountCharge.getCharge().getId())) {
                        savingsAccountCharge.updateCalculationTypeAndAmount(entry.getValue(), savingsAccountCharge.getCharge()
                                .chargeCalculationType());
                        isChargeLinked = true;
                        break;
                    }
                }
                if(!isChargeLinked){
                    nonLinkedCharges.add(entry.getKey());
                }
            }
        }

        if(!CollectionUtils.isEmpty(nonLinkedCharges)){
            for (Long chargeId : nonLinkedCharges) {
                baseDataValidator.reset().parameter(chargeIdParamName).value(chargeId).failWithCode("not.linked.to.payment.type", chargeId, paymentTypeId);
            }            
        }
        
        if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }
        return linkedCharges;
    }
}