CREATE TABLE loan (
    id            BIGINT GENERATED ALWAYS AS IDENTITY,
    book_item_id  BIGINT  NOT NULL,
    reader_id     BIGINT  NOT NULL,
    borrowed_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    due_date      DATE    NOT NULL,
    returned_at   TIMESTAMP WITH TIME ZONE,
    renewals      INTEGER NOT NULL DEFAULT 0,
    version       BIGINT  NOT NULL DEFAULT 0,

    CONSTRAINT pk_loan PRIMARY KEY (id),
    -- RESTRICT: egzemplarza ani czytelnika z historią wypożyczeń nie da się usunąć
    CONSTRAINT fk_loan_book_item FOREIGN KEY (book_item_id) REFERENCES book_item (id) ON DELETE RESTRICT,
    CONSTRAINT fk_loan_reader FOREIGN KEY (reader_id) REFERENCES reader (id) ON DELETE RESTRICT,
    CONSTRAINT chk_loan_due_date_not_before_borrowed CHECK (due_date >= (borrowed_at AT TIME ZONE 'UTC')::date),
    CONSTRAINT chk_loan_returned_not_before_borrowed CHECK (returned_at IS NULL OR returned_at >= borrowed_at),
    CONSTRAINT chk_loan_renewals_not_negative CHECK (renewals >= 0)
);

-- Jeden egzemplarz może mieć co najwyżej jedno aktywne (niezwrócone) wypożyczenie.
-- Częściowy indeks unikalny pilnuje tego w bazie, także przy równoczesnych żądaniach.
CREATE UNIQUE INDEX uq_loan_active_book_item ON loan (book_item_id) WHERE returned_at IS NULL;

CREATE INDEX idx_loan_book_item_id ON loan (book_item_id);
CREATE INDEX idx_loan_reader_id ON loan (reader_id);
-- Przyspiesza szukanie przeterminowanych wypożyczeń (niezwrócone, po terminie).
CREATE INDEX idx_loan_active_due_date ON loan (due_date) WHERE returned_at IS NULL;

COMMENT ON TABLE loan IS 'Wypożyczenia: który egzemplarz wypożyczył który czytelnik i na jak długo';
COMMENT ON COLUMN loan.id IS 'Identyfikator wypożyczenia nadawany przez bazę';
COMMENT ON COLUMN loan.book_item_id IS 'Wypożyczony egzemplarz książki';
COMMENT ON COLUMN loan.reader_id IS 'Czytelnik, który wypożyczył egzemplarz';
COMMENT ON COLUMN loan.borrowed_at IS 'Data i godzina wypożyczenia';
COMMENT ON COLUMN loan.due_date IS 'Termin zwrotu (ostatni dzień, w którym zwrot nie jest opóźniony)';
COMMENT ON COLUMN loan.returned_at IS 'Data i godzina zwrotu; pusta, dopóki egzemplarz nie wróci do biblioteki';
COMMENT ON COLUMN loan.renewals IS 'Ile razy przedłużono termin zwrotu';
COMMENT ON COLUMN loan.version IS 'Wersja wiersza do optymistycznego blokowania równoczesnej edycji';