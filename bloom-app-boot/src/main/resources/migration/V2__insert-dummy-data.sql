INSERT INTO item_categories (id, name, code, active, created_at, created_by)
VALUES
    (1, 'Bahan Bangunan', 'BB', true, now(), 'admin'),
    (2, 'Peralatan', 'PRT', true, now(), 'admin'),
    (3, 'Lainnya', 'LNN', true, now(), 'admin');

INSERT INTO items (name, sku, description, price, stock_quantity, active, item_category_id, created_at, created_by)
VALUES
    ('Triplek', 'BB-00001', 'Triplek kuat dan kokoh', 100000.00, 50, true, 1, now(), 'admin'),
    ('Batu Bata', 'BB-00002', 'Enak buat di lempar', 1000.00, 400, true, 1, now(), 'admin'),
    ('Paku Tajam', 'PRT-00001', 'Paku yang bisa menembus', 100.00, 0, true, 2, now(), 'admin'),
    ('Palu Thor', 'PRT-00002', 'Mjolnir, apalagi?', 999999.00, 10, true, 2, now(), 'admin'),
    ('Kabel Super Kuat', 'LNN-00001', 'Kabelnya kuat', 200.00, 30, true, 3, now(), 'admin'),
    ('Tameng Kapten Indonesia', 'LNN-00002', 'kena pajak, jadi ga ada lagi', 9.00, 10, false, 3, now(), 'admin');

INSERT INTO users (username, password, role, name, created_at, updated_at, created_by, updated_by)
VALUES ('admin', '$2a$12$y1sAyuZ237dP7nFW.hwShe9UT9lfxALNRUhfp9WmD2/ARa1P6gjxa', 'admin', 'Admin', now(), now(), 'SYSTEM', 'SYSTEM');

SELECT setval('items_id_seq', (SELECT MAX(id) FROM items));

SELECT setval('item_categories_id_seq', (SELECT MAX(id) FROM item_categories));

SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));