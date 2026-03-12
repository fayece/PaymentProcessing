INSERT INTO accounts (id, name, iban, balance, status, created_at, version)
VALUES
    (gen_random_uuid(), 'Alice', 'NL13TEST0123456789', 400.00, 'ACTIVE', NOW(), 0),
    (gen_random_uuid(), 'Bob',   'NL65TEST0987656789', 200.00, 'ACTIVE', NOW(), 0);