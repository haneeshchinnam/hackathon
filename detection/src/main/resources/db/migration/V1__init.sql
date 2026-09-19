CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(20) NOT NULL CONSTRAINT uk_users_username UNIQUE,
    email VARCHAR(50) NOT NULL CONSTRAINT uk_users_email UNIQUE,
    password VARCHAR(120) NOT NULL
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN')),
    PRIMARY KEY (user_id, role)
)