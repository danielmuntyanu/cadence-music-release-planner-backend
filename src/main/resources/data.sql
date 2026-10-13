-- Seed data: demo accounts for local development and testing.

-- Users
INSERT INTO users (username, email, password_hash, enabled, locked, created_at)
SELECT 'user1', 'user1@cadence.com',
       '$2a$10$5d2eD4avTyiyeTAODg/0LO0o7NCW/pDIR7mUg.CxIwp2JokwkqW8S',
       TRUE, FALSE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'user1');

INSERT INTO users (username, email, password_hash, enabled, locked, created_at)
SELECT 'admin1', 'admin1@cadence.com',
       '$2a$10$AFE5tidx3.lU0pSrM8jzZuZ.1hqevplf1oAo.1EF57oz7g8dQNOyC',
       TRUE, FALSE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin1');

-- Roles (resolved by username, so generated IDs never need to be known)
INSERT INTO user_roles (user_id, role)
SELECT u.id, 'USER' FROM users u
WHERE u.username = 'user1'
  AND NOT EXISTS (SELECT 1 FROM user_roles r WHERE r.user_id = u.id AND r.role = 'USER');

INSERT INTO user_roles (user_id, role)
SELECT u.id, 'USER' FROM users u
WHERE u.username = 'admin1'
  AND NOT EXISTS (SELECT 1 FROM user_roles r WHERE r.user_id = u.id AND r.role = 'USER');

INSERT INTO user_roles (user_id, role)
SELECT u.id, 'ADMIN' FROM users u
WHERE u.username = 'admin1'
  AND NOT EXISTS (SELECT 1 FROM user_roles r WHERE r.user_id = u.id AND r.role = 'ADMIN');

-- Profiles (primary key is shared with users via @MapsId)
INSERT INTO user_profiles (user_id, display_name)
SELECT u.id, 'User 1' FROM users u
WHERE u.username = 'user1'
  AND NOT EXISTS (SELECT 1 FROM user_profiles p WHERE p.user_id = u.id);

INSERT INTO user_profiles (user_id, display_name)
SELECT u.id, 'Admin 1' FROM users u
WHERE u.username = 'admin1'
  AND NOT EXISTS (SELECT 1 FROM user_profiles p WHERE p.user_id = u.id);