-- Tytuły książek (opis bibliograficzny): jeden wiersz na jeden numer ISBN.
CREATE TABLE book (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title            VARCHAR(255) NOT NULL,
    author           VARCHAR(255) NOT NULL,
    publication_year INTEGER      NOT NULL,
    isbn             VARCHAR(13)  NOT NULL,
    genre            VARCHAR(32)  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_book_isbn UNIQUE (isbn),
    CONSTRAINT ck_book_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_book_author_not_blank CHECK (btrim(author) <> ''),
    CONSTRAINT ck_book_publication_year CHECK (publication_year >= 1450),
    CONSTRAINT ck_book_isbn_format CHECK (isbn ~ '^[0-9]{13}$'),
    CONSTRAINT ck_book_genre CHECK (genre IN (
        'NOVEL', 'FANTASY', 'SCIENCE_FICTION', 'CRIME', 'HISTORY',
        'BIOGRAPHY', 'SCIENCE', 'CHILDREN', 'OTHER'))
);

COMMENT ON TABLE book IS 'Tytuły książek (opis bibliograficzny). Fizyczne egzemplarze są w tabeli book_item.';
COMMENT ON COLUMN book.title IS 'Tytuł książki';
COMMENT ON COLUMN book.author IS 'Autor książki';
COMMENT ON COLUMN book.publication_year IS 'Rok wydania (od 1450; górną granicę sprawdza aplikacja)';
COMMENT ON COLUMN book.isbn IS 'Numer ISBN-13 bez myślników (ISBN-10 jest przed zapisem zamieniany na ISBN-13)';
COMMENT ON COLUMN book.genre IS 'Gatunek: nazwa stałej z enuma Genre';
COMMENT ON COLUMN book.version IS 'Licznik wersji do optymistycznego blokowania (wykrywa równoczesną edycję)';

-- Fizyczne egzemplarze książek stojące na półce.
CREATE TABLE book_item (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    book_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_book_item_book FOREIGN KEY (book_id) REFERENCES book (id)
);

CREATE INDEX idx_book_item_book_id ON book_item (book_id);

COMMENT ON TABLE book_item IS 'Fizyczny egzemplarz książki na półce. Dostępność wynika z wypożyczeń, nie jest kolumną.';
COMMENT ON COLUMN book_item.book_id IS 'Tytuł, którego egzemplarzem jest ten wiersz (klucz obcy do book.id)';
COMMENT ON COLUMN book_item.version IS 'Licznik wersji do optymistycznego blokowania';