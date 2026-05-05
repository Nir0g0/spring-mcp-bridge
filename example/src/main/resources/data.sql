INSERT INTO customer (id, name, region, signup_date) VALUES
    (1, 'Shenzhen Beidou Robotics', 'CN-SOUTH', DATE '2024-09-12'),
    (2, 'Hangzhou Hexagon Logistics', 'CN-EAST', DATE '2024-10-04'),
    (3, 'Foshan Tinywire Industrial', 'CN-SOUTH', DATE '2025-01-22'),
    (4, 'Munich Carbon Composites GmbH', 'EU-DE', DATE '2025-02-18'),
    (5, 'Stuttgart Press-Tooling AG', 'EU-DE', DATE '2025-03-09'),
    (6, 'Dongguan Plasticon Mould', 'CN-SOUTH', DATE '2025-04-01')
ON CONFLICT (id) DO NOTHING;

INSERT INTO invoice (id, customer_id, amount_cents, issued_at) VALUES
    (1, 1, 1280000, TIMESTAMP '2025-02-03 09:42:00+00'),
    (2, 1, 740000,  TIMESTAMP '2025-03-19 13:12:00+00'),
    (3, 2, 312000,  TIMESTAMP '2025-01-28 17:30:00+00'),
    (4, 3, 88000,   TIMESTAMP '2025-04-04 08:00:00+00'),
    (5, 4, 4350000, TIMESTAMP '2025-03-22 11:11:00+00'),
    (6, 5, 1920000, TIMESTAMP '2025-04-12 16:05:00+00'),
    (7, 6, 64000,   TIMESTAMP '2025-04-15 09:17:00+00')
ON CONFLICT (id) DO NOTHING;
