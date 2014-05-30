/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.mifosplatform.portfolio.charge.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.mifosplatform.infrastructure.codes.domain.CodeValue;
import org.mifosplatform.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.mifosplatform.infrastructure.core.api.JsonCommand;
import org.mifosplatform.infrastructure.core.data.ApiParameterError;
import org.mifosplatform.infrastructure.core.data.CommandProcessingResult;
import org.mifosplatform.infrastructure.core.data.CommandProcessingResultBuilder;
import org.mifosplatform.infrastructure.core.data.DataValidatorBuilder;
import org.mifosplatform.infrastructure.core.exception.PlatformApiDataValidationException;
import org.mifosplatform.infrastructure.core.exception.PlatformDataIntegrityException;
import org.mifosplatform.infrastructure.core.serialization.FromJsonHelper;
import org.mifosplatform.infrastructure.core.service.RoutingDataSource;
import org.mifosplatform.portfolio.charge.domain.Charge;
import org.mifosplatform.portfolio.charge.domain.ChargeRepository;
import org.mifosplatform.portfolio.charge.domain.PaymentTypeCharge;
import org.mifosplatform.portfolio.charge.domain.PaymentTypeChargeRepository;
import org.mifosplatform.portfolio.charge.exception.ChargeCannotBeDeletedException;
import org.mifosplatform.portfolio.charge.exception.ChargeCannotBeUpdatedException;
import org.mifosplatform.portfolio.charge.exception.ChargeNotFoundException;
import org.mifosplatform.portfolio.charge.serialization.ChargeDefinitionCommandFromApiJsonDeserializer;
import org.mifosplatform.portfolio.loanproduct.domain.LoanProduct;
import org.mifosplatform.portfolio.loanproduct.domain.LoanProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@Service
public class ChargeWritePlatformServiceJpaRepositoryImpl implements ChargeWritePlatformService {

    private final static Logger logger = LoggerFactory.getLogger(ChargeWritePlatformServiceJpaRepositoryImpl.class);

    private final ChargeDefinitionCommandFromApiJsonDeserializer fromApiJsonDeserializer;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final ChargeRepository chargeRepository;
    private final LoanProductRepository loanProductRepository;
    private final FromJsonHelper fromApiJsonHelper;
    private final CodeValueRepositoryWrapper codeValueRepositoryWrapper;
    private final PaymentTypeChargeRepository paymentTypeChargeRepository;

    @Autowired
    public ChargeWritePlatformServiceJpaRepositoryImpl(final ChargeDefinitionCommandFromApiJsonDeserializer fromApiJsonDeserializer,
            final ChargeRepository chargeRepository, final LoanProductRepository loanProductRepository, final RoutingDataSource dataSource,
            final FromJsonHelper fromApiJsonHelper, final CodeValueRepositoryWrapper codeValueRepositoryWrapper,
            final PaymentTypeChargeRepository paymentTypeChargeRepository) {
        this.fromApiJsonDeserializer = fromApiJsonDeserializer;
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(this.dataSource);
        this.chargeRepository = chargeRepository;
        this.loanProductRepository = loanProductRepository;
        this.fromApiJsonHelper = fromApiJsonHelper;
        this.codeValueRepositoryWrapper = codeValueRepositoryWrapper;
        this.paymentTypeChargeRepository = paymentTypeChargeRepository;
    }

    @Transactional
    @Override
    @CacheEvict(value = "charges", key = "T(org.mifosplatform.infrastructure.core.service.ThreadLocalContextUtil).getTenant().getTenantIdentifier().concat('ch')")
    public CommandProcessingResult createCharge(final JsonCommand command) {
        try {
            this.fromApiJsonDeserializer.validateForCreate(command.json());

            final Charge charge = Charge.fromJson(command);
            this.chargeRepository.save(charge);

            final Collection<PaymentTypeCharge> paymentTypesCharges = getPaymentTypeCharges(charge, command);
            this.paymentTypeChargeRepository.save(paymentTypesCharges);

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(charge.getId()).build();
        } catch (final DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(command, dve);
            return CommandProcessingResult.empty();
        }
    }

    private Collection<PaymentTypeCharge> getPaymentTypeCharges(Charge charge, JsonCommand command) {
        final JsonElement element = command.parsedJson();
        final Locale locale = command.extractLocale();
        Collection<PaymentTypeCharge> paymentTypeCharges = new ArrayList<PaymentTypeCharge>();
        if (element.isJsonObject()) {
            final JsonObject topLevelJsonElement = element.getAsJsonObject();
            if (topLevelJsonElement.has("paymentTypes") && topLevelJsonElement.get("paymentTypes").isJsonArray()) {
                final JsonArray array = topLevelJsonElement.get("paymentTypes").getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    final JsonObject chargePaymentTypeElement = array.get(i).getAsJsonObject();

                    final Long paymentTypeId = this.fromApiJsonHelper.extractLongNamed("id", chargePaymentTypeElement);
                    final Integer calculationTypeId = this.fromApiJsonHelper.extractIntegerSansLocaleNamed("chargeCalculationType",
                            chargePaymentTypeElement);
                    final BigDecimal amount = this.fromApiJsonHelper.extractBigDecimalNamed("amount", chargePaymentTypeElement, locale);
                    final CodeValue paymentType = this.codeValueRepositoryWrapper.findOneByCodeNameAndIdWithNotFoundDetection(
                            "PaymentType", paymentTypeId);
                    PaymentTypeCharge paymentTypeCharge = PaymentTypeCharge.create(paymentType, charge, calculationTypeId, amount);
                    paymentTypeCharges.add(paymentTypeCharge);
                }
            }
        }

        return paymentTypeCharges;
    }

    @Transactional
    @Override
    @CacheEvict(value = "charges", key = "T(org.mifosplatform.infrastructure.core.service.ThreadLocalContextUtil).getTenant().getTenantIdentifier().concat('ch')")
    public CommandProcessingResult updateCharge(final Long chargeId, final JsonCommand command) {

        try {
            this.fromApiJsonDeserializer.validateForUpdate(command.json());

            final Charge chargeForUpdate = this.chargeRepository.findOne(chargeId);
            if (chargeForUpdate == null) { throw new ChargeNotFoundException(chargeId); }

            final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
            final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("charges");

            final Map<String, Object> changes = chargeForUpdate.update(command, baseDataValidator);

            // MIFOSX-900: Check if the Charge has been active before and now is
            // deactivated:
            if (changes.containsKey("active")) {
                // IF the key exists then it has changed (otherwise it would
                // have been filtered), so check current state:
                if (!chargeForUpdate.isActive()) {
                    // TODO: Change this function to only check the mappings!!!
                    final Boolean isChargeExistWithLoans = isAnyLoanProductsAssociateWithThisCharge(chargeId);
                    final Boolean isChargeExistWithSavings = isAnySavingsProductsAssociateWithThisCharge(chargeId);

                    if (isChargeExistWithLoans || isChargeExistWithSavings) { throw new ChargeCannotBeUpdatedException(
                            "error.msg.charge.cannot.be.updated.it.is.used.in.loan", "This charge cannot be updated, it is used in loan"); }
                }
            } else if ((changes.containsKey("feeFrequency") || changes.containsKey("feeInterval")) && chargeForUpdate.isLoanCharge()) {
                final Boolean isChargeExistWithLoans = isAnyLoanProductsAssociateWithThisCharge(chargeId);
                if (isChargeExistWithLoans) { throw new ChargeCannotBeUpdatedException(
                        "error.msg.charge.frequency.cannot.be.updated.it.is.used.in.loan",
                        "This charge frequency cannot be updated, it is used in loan"); }
            }

            changes.put("paymentTypes", updatePaymentTypeCharges(command, chargeForUpdate, baseDataValidator));

            if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException(dataValidationErrors); }

            if (!changes.isEmpty()) {
                this.chargeRepository.save(chargeForUpdate);
            }

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(chargeId).with(changes).build();
        } catch (final DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(command, dve);
            return CommandProcessingResult.empty();
        }
    }

    @SuppressWarnings("null")
    private Map<String, Object> updatePaymentTypeCharges(JsonCommand command, final Charge charge,
            final DataValidatorBuilder baseDataValidator) {
        final JsonElement element = command.parsedJson();
        final Locale locale = command.extractLocale();
        final String localeAsInput = command.locale();
        final Map<String, Object> actualChanges = new LinkedHashMap<String, Object>();
        final Set<PaymentTypeCharge> updatedPaymentTypeCharges = new HashSet<PaymentTypeCharge>();
        if (element.isJsonObject()) {
            final JsonObject topLevelJsonElement = element.getAsJsonObject();
            if (topLevelJsonElement.has("paymentTypes") && topLevelJsonElement.get("paymentTypes").isJsonArray()) {
                final JsonArray array = topLevelJsonElement.get("paymentTypes").getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    final JsonObject chargePaymentTypeElement = array.get(i).getAsJsonObject();
                    final Long paymentTypeId = this.fromApiJsonHelper.extractLongNamed("id", chargePaymentTypeElement);
                    PaymentTypeCharge ptCharge = charge.findPaymentTypeChargeByPaymentType(paymentTypeId);
                    final Map<String, Object> ptChargeChanges = new LinkedHashMap<String, Object>(2);
                    // add the payment type charge
                    if (ptCharge == null) {
                        final Integer calculationTypeId = this.fromApiJsonHelper.extractIntegerSansLocaleNamed("chargeCalculationType",
                                chargePaymentTypeElement);
                        final BigDecimal amount = this.fromApiJsonHelper.extractBigDecimalNamed("amount", chargePaymentTypeElement, locale);
                        final CodeValue paymentType = this.codeValueRepositoryWrapper.findOneByCodeNameAndIdWithNotFoundDetection(
                                "PaymentType", paymentTypeId);
                        ptCharge = PaymentTypeCharge.create(paymentType, charge, calculationTypeId, amount);
                        
                        /*ptChargeChanges.put("paymentType", ptCharge.paymentType().getId());
                        ptChargeChanges.put("chargeCalculationType", ptCharge.chargeCalculationType().getValue());
                        ptChargeChanges.put("amount", ptCharge.amount());
                        ptChargeChanges.put("locale", locale);
                        actualChanges.put(ptCharge.getId().toString(), ptChargeChanges);*/
                    } else {
                        // update existing payment type charge
                        final JsonCommand paymentTypeCommand = JsonCommand.fromExistingCommand(command, chargePaymentTypeElement);
                        ptChargeChanges.putAll(ptCharge.update(paymentTypeCommand, baseDataValidator, localeAsInput));
                        
                    }
                    
                    if (ptChargeChanges != null && !ptChargeChanges.isEmpty()) {
                        actualChanges.put(ptCharge.getId().toString(), ptChargeChanges);
                    }
                    
                    updatedPaymentTypeCharges.add(ptCharge);
                }
            }
        }

        // FIXME: Need to handle this in better way
        // Payment Type Charge is deleted if its not part of update request.
        charge.updatePaymentTypeCharges(updatedPaymentTypeCharges, baseDataValidator);

        return actualChanges;
    }

    @Transactional
    @Override
    @CacheEvict(value = "charges", key = "T(org.mifosplatform.infrastructure.core.service.ThreadLocalContextUtil).getTenant().getTenantIdentifier().concat('ch')")
    public CommandProcessingResult deleteCharge(final Long chargeId) {

        final Charge chargeForDelete = this.chargeRepository.findOne(chargeId);
        if (chargeForDelete == null || chargeForDelete.isDeleted()) { throw new ChargeNotFoundException(chargeId); }

        final Collection<LoanProduct> loanProducts = this.loanProductRepository.retrieveLoanProductsByChargeId(chargeId);
        final Boolean isChargeExistWithLoans = isAnyLoansAssociateWithThisCharge(chargeId);
        final Boolean isChargeExistWithSavings = isAnySavingsAssociateWithThisCharge(chargeId);

        // TODO: Change error messages around:
        if (!loanProducts.isEmpty() || isChargeExistWithLoans || isChargeExistWithSavings) { throw new ChargeCannotBeDeletedException(
                "error.msg.charge.cannot.be.deleted.it.is.already.used.in.loan",
                "This charge cannot be deleted, it is already used in loan"); }

        chargeForDelete.delete();

        this.chargeRepository.save(chargeForDelete);

        return new CommandProcessingResultBuilder().withEntityId(chargeForDelete.getId()).build();
    }

    /*
     * Guaranteed to throw an exception no matter what the data integrity issue
     * is.
     */
    private void handleDataIntegrityIssues(final JsonCommand command, final DataIntegrityViolationException dve) {

        final Throwable realCause = dve.getMostSpecificCause();
        if (realCause.getMessage().contains("name")) {
            final String name = command.stringValueOfParameterNamed("name");
            throw new PlatformDataIntegrityException("error.msg.charge.duplicate.name", "Charge with name `" + name + "` already exists",
                    "name", name);
        } else if (realCause.getMessage().contains("unique_payment_type_charge")) { throw new PlatformDataIntegrityException(
                "error.msg.charge.duplicate.payment.type.mapping", "A payment type cannot be mapped more than once"); }

        logger.error(dve.getMessage(), dve);
        throw new PlatformDataIntegrityException("error.msg.charge.unknown.data.integrity.issue",
                "Unknown data integrity issue with resource: " + realCause.getMessage());
    }

    private boolean isAnyLoansAssociateWithThisCharge(final Long chargeId) {

        final String sql = "select if((exists (select 1 from m_loan_charge lc where lc.charge_id = ?)) = 1, 'true', 'false')";
        final String isLoansUsingCharge = this.jdbcTemplate.queryForObject(sql, String.class, new Object[] { chargeId });
        return new Boolean(isLoansUsingCharge);
    }

    private boolean isAnySavingsAssociateWithThisCharge(final Long chargeId) {

        final String sql = "select if((exists (select 1 from m_savings_account_charge sc where sc.charge_id = ?)) = 1, 'true', 'false')";
        final String isSavingsUsingCharge = this.jdbcTemplate.queryForObject(sql, String.class, new Object[] { chargeId });
        return new Boolean(isSavingsUsingCharge);
    }

    private boolean isAnyLoanProductsAssociateWithThisCharge(final Long chargeId) {

        final String sql = "select if((exists (select 1 from m_product_loan_charge lc where lc.charge_id = ?)) = 1, 'true', 'false')";
        final String isLoansUsingCharge = this.jdbcTemplate.queryForObject(sql, String.class, new Object[] { chargeId });
        return new Boolean(isLoansUsingCharge);
    }

    private boolean isAnySavingsProductsAssociateWithThisCharge(final Long chargeId) {

        final String sql = "select if((exists (select 1 from m_savings_product_charge sc where sc.charge_id = ?)) = 1, 'true', 'false')";
        final String isSavingsUsingCharge = this.jdbcTemplate.queryForObject(sql, String.class, new Object[] { chargeId });
        return new Boolean(isSavingsUsingCharge);
    }
}
