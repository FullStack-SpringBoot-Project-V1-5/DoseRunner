-- One demo user per role. DEV ONLY: every seed user's password is "DoseRunner-2026" (see the README).
-- Fixed ids so tests and demo scripts can refer to them.
insert into app_user (id, username, password_hash) values
    ('00000000-0000-0000-0000-000000000001', 'nurse',      '$2a$10$dwfqoGmY5XIuUQzYg5f9HeHGKF1C89PelXZsfJpNMPKDIg3Fd6xFq'),
    ('00000000-0000-0000-0000-000000000002', 'pharmacist', '$2a$10$dwfqoGmY5XIuUQzYg5f9HeHGKF1C89PelXZsfJpNMPKDIg3Fd6xFq'),
    ('00000000-0000-0000-0000-000000000003', 'operator',   '$2a$10$dwfqoGmY5XIuUQzYg5f9HeHGKF1C89PelXZsfJpNMPKDIg3Fd6xFq'),
    ('00000000-0000-0000-0000-000000000004', 'auditor',    '$2a$10$dwfqoGmY5XIuUQzYg5f9HeHGKF1C89PelXZsfJpNMPKDIg3Fd6xFq'),
    ('00000000-0000-0000-0000-000000000005', 'admin',      '$2a$10$dwfqoGmY5XIuUQzYg5f9HeHGKF1C89PelXZsfJpNMPKDIg3Fd6xFq');

insert into user_role (user_id, role) values
    ('00000000-0000-0000-0000-000000000001', 'NURSE'),
    ('00000000-0000-0000-0000-000000000002', 'PHARMACIST'),
    ('00000000-0000-0000-0000-000000000003', 'OPERATOR'),
    ('00000000-0000-0000-0000-000000000004', 'AUDITOR'),
    ('00000000-0000-0000-0000-000000000005', 'ADMIN');
