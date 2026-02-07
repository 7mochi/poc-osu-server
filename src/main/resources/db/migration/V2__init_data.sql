INSERT INTO users (id, username, email, password_md5, country, privileges)
VALUES (1, 'BanchoBot', 'bancho@osupe.ru', '098f6bcd4621d373cade4e832627b4f6', 118, 1),
       (3, 'test', 'test@gmail.com', '098f6bcd4621d373cade4e832627b4f6', 118, 1),
       (4, 'test2', 'test2@gmail.com', '098f6bcd4621d373cade4e832627b4f6', 13, 0),
       (5, 'test3', 'test3@gmail.com', '098f6bcd4621d373cade4e832627b4f6', 13, 1);

ALTER SEQUENCE user_seq RESTART WITH 5;

INSERT INTO channels (id, name, topic, read_privileges, write_privileges, auto_join, temporary, created_at, updated_at)
VALUES (UUID(), '#osu', 'General discussion.', 0, 1, 1, false, NOW(), NOW()),
       (UUID(), '#lobby', 'General multiplayer lobby chat.', 0, 1, 0, false, NOW(), NOW()),
       (UUID(), '#announce', 'Announcements from the server.', 0, 1 << 30, 1, false, NOW(), NOW()),
       (UUID(), '#help', 'Help and support.', 1, 0, 1, false, NOW(), NOW()),
       (UUID(), '#staff', 'General discussion for staff members.', (1 << 7 | 1 << 9 | 1 << 13 | 1 << 30),
        (1 << 7 | 1 << 9 | 1 << 13 | 1 << 30),
        1, false, NOW(), NOW()),
       (UUID(), '#dev', 'General discussion for developers.', 1 << 30, 1 << 30, 1, false, NOW(), NOW());

INSERT INTO stats (user_id, gamemode)
VALUES (1, 0),
       (1, 1),
       (1, 2),
       (1, 3);

INSERT INTO stats (user_id, gamemode)
VALUES (3, 0),
       (3, 1),
       (3, 2),
       (3, 3);

INSERT INTO stats (user_id, gamemode)
VALUES (4, 0),
       (4, 1),
       (4, 2),
       (4, 3);

INSERT INTO stats (user_id, gamemode)
VALUES (5, 0),
       (5, 1),
       (5, 2),
       (5, 3);
