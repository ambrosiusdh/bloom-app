INSERT INTO items (name, sku, description, price, stock_quantity, active, created_at, created_by)
VALUES
    ('Triplek', 'SKU-1', 'Triplek kuat dan kokoh', 100000.00, 50, true, now(), 'admin'),
    ('Batu Bata', 'SKU-2', 'Enak buat di lempar', 1000.00, 400, true, now(), 'admin'),
    ('Paku Tajam', 'SKU-3', 'Paku yang bisa menembus', 100.00, 0, true, now(), 'admin'),
    ('Palu Thor', 'SKU-4', 'Mjolnir, apalagi?', 999999.00, 10, true, now(), 'admin'),
    ('Kabel Super Kuat', 'SKU-5', 'Kabelnya kuat', 200.00, 30, true, now(), 'admin'),
    ('Tameng Kapten Indonesia', 'SKU-6', 'kena pajak, jadi ga ada lagi', 9.00, 10, false, now(), 'admin');

INSERT INTO users (username, password, role, name, created_at, updated_at, created_by, updated_by)
VALUES ('admin', '$2a$12$y1sAyuZ237dP7nFW.hwShe9UT9lfxALNRUhfp9WmD2/ARa1P6gjxa', 'admin', 'Admin', now(), now(), 'SYSTEM', 'SYSTEM')
