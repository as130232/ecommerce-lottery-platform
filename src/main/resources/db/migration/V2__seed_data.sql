-- ---------------------------------------------------------------------------
-- Seed data for local / demo use.
-- Accounts (bcrypt): admin/admin123 (ADMIN), alice/alice123 (USER)
-- ---------------------------------------------------------------------------

INSERT INTO app_user (username, password_hash, role) VALUES
    ('admin', '$2a$10$6sKz0vPw6YMbjA6ntM9za.2YsUN5DLF7eJmzunRGU7xEjRAVihkQa', 'ADMIN'),
    ('alice', '$2a$10$PEC3jXuLJiwAgVJRFiiS2u5nNDeEwp1iFn.SUVfJvkh5Z0/yigFrO', 'USER');

INSERT INTO lottery_activity (code, name, status, per_user_draw_limit, total_draw_limit)
VALUES ('SPRING2026', '2026 春季轉盤抽獎', 'ACTIVE', 10, NULL);

-- prizes: 2% + 3% + 5% + 90% = 100%
INSERT INTO prize (activity_id, name, type, probability, total_stock, remaining_stock)
SELECT id, '頭獎 iPhone 16',  'PRIZE',   200,  3,  3 FROM lottery_activity WHERE code = 'SPRING2026';
INSERT INTO prize (activity_id, name, type, probability, total_stock, remaining_stock)
SELECT id, '二獎 AirPods',    'PRIZE',   300, 10, 10 FROM lottery_activity WHERE code = 'SPRING2026';
INSERT INTO prize (activity_id, name, type, probability, total_stock, remaining_stock)
SELECT id, '三獎 折價券 100', 'PRIZE',   500, 50, 50 FROM lottery_activity WHERE code = 'SPRING2026';
INSERT INTO prize (activity_id, name, type, probability, total_stock, remaining_stock)
SELECT id, '銘謝惠顧',        'THANKS', 9000,  0,  0 FROM lottery_activity WHERE code = 'SPRING2026';
