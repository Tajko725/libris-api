-- Sekwencja numerów kart bibliotecznych (LB-000001, LB-000002, ...).
-- Maksimum 999999 jest celowe: numer ma stałą długość 6 cyfr, a po wyczerpaniu zakresu
-- sekwencja zgłosi błąd zamiast powtórzyć numer.
CREATE SEQUENCE reader_card_number_seq
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 999999
    NO CYCLE;

CREATE TABLE reader (
    id            BIGINT GENERATED ALWAYS AS IDENTITY,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    phone         VARCHAR(20),
    card_number   VARCHAR(9)   NOT NULL
        DEFAULT ('LB-' || lpad(nextval('reader_card_number_seq')::text, 6, '0')),
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_reader PRIMARY KEY (id),
    CONSTRAINT uq_reader_email UNIQUE (email),
    CONSTRAINT uq_reader_card_number UNIQUE (card_number),
    CONSTRAINT chk_reader_first_name_not_blank CHECK (btrim(first_name) <> ''),
    CONSTRAINT chk_reader_last_name_not_blank CHECK (btrim(last_name) <> ''),
    CONSTRAINT chk_reader_email_normalized CHECK (email = lower(btrim(email))),
    CONSTRAINT chk_reader_email_format CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+$'),
    CONSTRAINT chk_reader_phone_format CHECK (phone IS NULL OR phone ~ '^[0-9 +-]{7,20}$')
);

COMMENT ON TABLE reader IS 'Czytelnicy biblioteki, czyli osoby, które mogą wypożyczać książki';
COMMENT ON COLUMN reader.id IS 'Identyfikator czytelnika nadawany przez bazę';
COMMENT ON COLUMN reader.first_name IS 'Imię czytelnika';
COMMENT ON COLUMN reader.last_name IS 'Nazwisko czytelnika';
COMMENT ON COLUMN reader.email IS 'Adres e-mail: unikalny, zapisywany małymi literami bez spacji na brzegach';
COMMENT ON COLUMN reader.phone IS 'Numer telefonu (opcjonalny): cyfry, spacje, plus i myślnik, 7-20 znaków';
COMMENT ON COLUMN reader.card_number IS 'Numer karty bibliotecznej nadawany z sekwencji, format LB-000001';
COMMENT ON COLUMN reader.registered_at IS 'Data i godzina rejestracji czytelnika';
COMMENT ON COLUMN reader.active IS 'Czy czytelnik może wypożyczać książki (false oznacza dezaktywowane konto)';
COMMENT ON COLUMN reader.version IS 'Wersja wiersza do optymistycznego blokowania równoczesnej edycji';