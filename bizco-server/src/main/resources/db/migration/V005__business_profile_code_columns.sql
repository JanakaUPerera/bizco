ALTER TABLE business_profile
    ALTER COLUMN country_code TYPE VARCHAR(2) USING trim(country_code),
    ALTER COLUMN currency_code TYPE VARCHAR(3) USING trim(currency_code);
