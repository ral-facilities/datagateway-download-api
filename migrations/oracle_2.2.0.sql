
CREATE TABLE CONFVAR (
  NAME varchar(255) NOT NULL,
  VALUE clob,
  PRIMARY KEY (NAME)
);

ALTER TABLE DOWNLOAD ADD THE_SIZE NUMBER(19, 0) DEFAULT 0 NOT NULL;
