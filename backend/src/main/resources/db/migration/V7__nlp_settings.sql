CREATE TABLE nlp_settings (
    id             smallint     PRIMARY KEY CHECK (id = 1),
    version        integer      NOT NULL,
    threshold      numeric(4,3) NOT NULL CHECK (threshold >= 0 AND threshold <= 1),
    trigger_words  text[]       NOT NULL,
    negation_words text[]       NOT NULL,
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    updated_by     text
);

CREATE TABLE nlp_settings_history (
    version        integer      PRIMARY KEY,
    threshold      numeric(4,3) NOT NULL,
    trigger_words  text[]       NOT NULL,
    negation_words text[]       NOT NULL,
    changed_at     timestamptz  NOT NULL DEFAULT now(),
    changed_by     text,
    comment        text
);

INSERT INTO nlp_settings (id, version, threshold, trigger_words, negation_words, updated_by)
VALUES (1, 1, 0.5,
        ARRAY['треть*','лиц*','интерес*','счет*','правопреемник*','довереннос*',
              'цессионар*','контрагент*','польз*','выгодоприобретател*'],
        ARRAY['не','нет','отсутств*','без'],
        'migration');

INSERT INTO nlp_settings_history (version, threshold, trigger_words, negation_words, changed_by, comment)
SELECT version, threshold, trigger_words, negation_words, updated_by, 'defaults'
FROM nlp_settings;