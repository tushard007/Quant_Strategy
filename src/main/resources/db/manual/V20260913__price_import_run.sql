CREATE TABLE IF NOT EXISTS price_import_run (
    id varchar(255) PRIMARY KEY,
    source varchar(255),
    time_frame varchar(255),
    status varchar(255),
    message text,
    processed integer NOT NULL DEFAULT 0,
    total integer NOT NULL DEFAULT 0,
    saved integer NOT NULL DEFAULT 0,
    failed_symbols text,
    updated_at timestamptz
);
