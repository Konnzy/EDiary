CREATE TABLE user_import_lock (
    id INTEGER PRIMARY KEY CHECK (id = 1)
);

INSERT INTO user_import_lock (id) VALUES (1);
