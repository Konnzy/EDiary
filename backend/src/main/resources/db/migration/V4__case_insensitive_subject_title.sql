CREATE UNIQUE INDEX uq_subject_title_ci
    ON subject (LOWER(subject_title));
