CREATE TABLE `m_payment_type_charge` (
     `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
     `charge_id` BIGINT(20) NOT NULL,
     `payment_type_id` INT(11) NOT NULL,
     `charge_calculation_type_enum` SMALLINT(5) NOT NULL,
     `amount` DECIMAL(19,6) NOT NULL,
     PRIMARY KEY (`id`),
     UNIQUE INDEX `unique_payment_type_charge` (`charge_id`, `payment_type_id`),
     INDEX `FKMCPT000000001` (`charge_id`),
     INDEX `FKMCPT000000002` (`payment_type_id`),
     CONSTRAINT `FKMCPT000000001` FOREIGN KEY (`charge_id`) REFERENCES `m_charge` (`id`),
     CONSTRAINT `FKMCPT000000002` FOREIGN KEY (`payment_type_id`) REFERENCES `m_code_value` (`id`)
);

ALTER TABLE `m_savings_account_transaction`
     ADD COLUMN `parent_id` BIGINT(20) NULL DEFAULT NULL AFTER `payment_detail_id`,
     ADD CONSTRAINT `FKSAT0000000002` FOREIGN KEY (`parent_id`) REFERENCES `m_savings_account_transaction` (`id`);

ALTER TABLE `m_savings_account`
     ADD COLUMN `deposit_fee_for_transfer` TINYINT(4) NULL DEFAULT '1' AFTER `withdrawal_fee_for_transfer`;

ALTER TABLE `m_savings_product`
     ADD COLUMN `deposit_fee_for_transfer` TINYINT(4) NULL DEFAULT '1' AFTER `withdrawal_fee_for_transfer`;

ALTER TABLE `m_savings_account_charge`
     ADD COLUMN `applicable_to_all_products` TINYINT(1) NULL DEFAULT NULL AFTER `is_active`,
     ADD COLUMN `payment_type_id` INT(11) NULL DEFAULT NULL AFTER `applicable_to_all_products`;

ALTER TABLE `m_charge`
     ADD COLUMN `applicable_to_all_products` TINYINT(1) NULL DEFAULT NULL AFTER `is_active`;
