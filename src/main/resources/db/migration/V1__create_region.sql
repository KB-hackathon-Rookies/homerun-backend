CREATE TABLE region (
                        id BIGSERIAL PRIMARY KEY,

                        code VARCHAR(20) NOT NULL UNIQUE,

                        name VARCHAR(50) NOT NULL,

                        level VARCHAR(20) NOT NULL
);