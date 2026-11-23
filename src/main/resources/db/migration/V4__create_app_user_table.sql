-- Konta pracowników biblioteki (bibliotekarze i administratorzy), którzy logują się do API.
-- Czytelnicy nie mają kont: są danymi w tabeli reader.
CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT chk_app_user_username_format CHECK (username ~ '^[a-z0-9._-]{3,50}$'),
    CONSTRAINT chk_app_user_role CHECK (role IN ('ADMIN', 'LIBRARIAN')),
    -- Hasło musi być hashem bcrypt z prefiksem algorytmu. Ograniczenie nie dopuszcza np. {noop}hasło,
    -- więc hasło jawnym tekstem nie trafi do bazy nawet przez pomyłkę albo wpis SQL-em.
    -- Inny algorytm wymaga nowej migracji, która rozluźni to ograniczenie.
    CONSTRAINT chk_app_user_password_hash_bcrypt
        CHECK (password_hash ~ '^\{bcrypt\}\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$')
);

COMMENT ON TABLE app_user IS 'Konta pracowników biblioteki, którzy logują się do API (czytelnicy nie mają kont)';
COMMENT ON COLUMN app_user.id IS 'Identyfikator użytkownika nadawany przez bazę';
COMMENT ON COLUMN app_user.username IS 'Nazwa użytkownika: unikalna, małe litery, cyfry oraz . _ -, 3-50 znaków';
COMMENT ON COLUMN app_user.password_hash IS 'Hash hasła bcrypt z prefiksem {bcrypt}; nigdy samo hasło';
COMMENT ON COLUMN app_user.role IS 'Rola: ADMIN (wszystko, w tym usuwanie i użytkownicy) albo LIBRARIAN (obsługa biblioteki bez usuwania)';
COMMENT ON COLUMN app_user.enabled IS 'Czy konto może się logować (false oznacza zablokowane konto)';
COMMENT ON COLUMN app_user.created_at IS 'Data i godzina utworzenia konta';
COMMENT ON COLUMN app_user.version IS 'Wersja wiersza do optymistycznego blokowania równoczesnej edycji';
