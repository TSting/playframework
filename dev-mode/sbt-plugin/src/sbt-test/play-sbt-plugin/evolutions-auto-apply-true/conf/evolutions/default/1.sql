# Initial test schema

# --- !Ups

CREATE TABLE users (
    id bigint GENERATED BY DEFAULT AS IDENTITY,
    username varchar(255) NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO users(username) VALUES ('PlayerFromFirstEvolution');

# --- !Downs

DROP TABLE users;
