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



-- Demo artists for user1
INSERT INTO artists (name, avatar_url, banner_url, description, genre, user_id)
VALUES
  (
    'Lía Montes',
    'https://images.unsplash.com/photo-1580559680359-e9780bba8912?q=80&w=774&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=NHwxMjA3fDA%3D',
    'https://images.unsplash.com/photo-1530982011887-3cc11cc85693?q=80&w=2064&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=NHwxMjA3fDA%3D',
    'Madrid-born singer-songwriter mixing glossy synth-pop hooks with diary-honest lyrics. Writes every chorus on a battered upright piano, then drowns it in 80s reverb. Currently finishing her debut EP.',
    'Pop',
    (SELECT id FROM users WHERE username = 'user1')
  ),
  (
    'Niebla Norte',
    'https://images.unsplash.com/photo-1620577610365-86c411bad78d?q=80&w=1740&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=NHwxMjA3fDA%3D',
    'https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?q=80&w=1548&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=NHwxMjA3fDA%3D',
    'Four-piece from the Asturian coast. Fuzzed-out guitars, sea fog and choruses built to be shouted in small rooms. Recorded their first album live in a fishermen''s warehouse in Gijón.',
    'Alternative',
    (SELECT id FROM users WHERE username = 'user1')
  );

INSERT INTO artist_socials (artist_id, platform, url)
VALUES
  ((SELECT id FROM artists WHERE name = 'Lía Montes'), 'WEBSITE', 'https://example.com/lía-montes'),
  ((SELECT id FROM artists WHERE name = 'Niebla Norte'), 'WEBSITE', 'https://example.com/niebla-norte');

-- Demo release
INSERT INTO releases (name, type, phase, artwork_url, notes, primary_genre, secondary_genre, release_date, created_at, user_id)
VALUES (
  'Neon Siesta',
  'EP',
  'PRODUCTION',
  'https://picsum.photos/seed/neon-siesta/1200/1200',
  'Debut EP. Synth stems for track 3 are in the shared drive: https://example.com/neon-siesta/stems',
  'POP',
  'ELECTRONIC',
  '2026-12-04',
  CURRENT_TIMESTAMP,
  (SELECT id FROM users WHERE username = 'user1')
);

INSERT INTO release_artists (release_id, artist_id, position)
VALUES (
  (SELECT id FROM releases WHERE name = 'Neon Siesta'),
  (SELECT id FROM artists WHERE name = 'Lía Montes'),
  0
);

INSERT INTO songs (release_id, title, position, duration_seconds, isrc, status, lyrics, explicit, created_at)
VALUES
  ((SELECT id FROM releases WHERE name = 'Neon Siesta'), 'Siesta Lights', 0, 204, 'ESZZZ2600001', 'DONE',
   'Shutters down at three o''clock
The city hums in coral light', false, CURRENT_TIMESTAMP),
  ((SELECT id FROM releases WHERE name = 'Neon Siesta'), 'Piano Before Midnight', 1, 231, 'ESZZZ2600002', 'MIXING_MASTERING',
   NULL, false, CURRENT_TIMESTAMP),
  ((SELECT id FROM releases WHERE name = 'Neon Siesta'), 'Coral Static', 2, 198, NULL, 'RECORDING',
   NULL, true, CURRENT_TIMESTAMP),
  ((SELECT id FROM releases WHERE name = 'Neon Siesta'), 'Paper Moon Avenue', 3, NULL, NULL, 'ARRANGEMENT',
   NULL, false, CURRENT_TIMESTAMP),
  ((SELECT id FROM releases WHERE name = 'Neon Siesta'), 'Untitled (Voice Memo 14)', 4, NULL, NULL, 'WRITING',
   NULL, false, CURRENT_TIMESTAMP);