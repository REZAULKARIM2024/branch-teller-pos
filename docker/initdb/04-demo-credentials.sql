-- The base schema ships 'admin' and 'teller1' with CHANGE_ME_HASH/CHANGE_ME_SALT
-- placeholders (see README "Setup" step 2) so nobody can log in until real hashes are
-- generated. For the Docker demo environment we pre-bake working demo credentials here,
-- generated the documented way (java -cp target/classes com.branchteller.util.PasswordUtil
-- <password>) so `docker compose up` gives a login-ready app out of the box:
--   admin    / admin123
--   teller1  / teller123
USE branch_teller;

UPDATE users SET password_hash = '+RDCeNYg6C4pdZPeQmg8jrZr7OTNKLrLBhvYfAGHq4o=',
                 salt = 'Ao/G0aCsW8OuOiPlYbaLwg=='
WHERE username = 'admin';

UPDATE users SET password_hash = '0Dl6DMYDTlxY0Ms01JVA8X+ok72LY9jpAM0T9V/lLnY=',
                 salt = '2MwumS1HTFnRnYMPSV5wiQ=='
WHERE username = 'teller1';
