-- =====================================================================
-- Geyser · Phase 9 · 01 Tabellen, Sequenzen, Trigger
-- Schema DEMO, Präfix GEY_. Jede Anweisung endet mit einer Zeile "/",
-- so läuft das Skript in SQL*Plus, SQL Developer (F5) und im Einrichter.
-- Texte in CHAR-Semantik (die Datenbank zählt sonst Bytes, Umlaute und
-- Tiefzahlen wie in SiO₂ brauchen mehrere).
-- Bricht ab, wenn es schon Objekte mit GEY_ gibt (dann erst 99_abbau.sql).
-- =====================================================================

DECLARE
  n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM user_objects WHERE object_name LIKE 'GEY\_%' ESCAPE '\';
  IF n > 0 THEN
    raise_application_error(-20901, 'Es gibt schon ' || n || ' Objekte mit GEY_. Erst 99_abbau.sql ausführen.');
  END IF;
END;
/

-- ------------------------------------------------------------- Belege
CREATE SEQUENCE gey_source_seq
/
CREATE TABLE gey_source (
  source_id      NUMBER        CONSTRAINT gey_source_pk PRIMARY KEY,
  code           VARCHAR2(30 CHAR)  NOT NULL CONSTRAINT gey_source_uk UNIQUE,
  titel          VARCHAR2(300 CHAR) NOT NULL,
  herausgeber    VARCHAR2(100 CHAR),
  url            VARCHAR2(400 CHAR) NOT NULL,
  art            VARCHAR2(12)       NOT NULL CONSTRAINT gey_source_art_ck CHECK (art IN ('BEHOERDE', 'LEXIKON', 'FACHARTIKEL', 'DATEN')),
  abgerufen      DATE
)
/
CREATE OR REPLACE TRIGGER gey_source_bi BEFORE INSERT ON gey_source FOR EACH ROW
BEGIN
  IF :new.source_id IS NULL THEN :new.source_id := gey_source_seq.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE gey_source IS 'Geyser: Belege (NPS, USGS, Wikipedia, Fachartikel), jeder einmal'
/

CREATE SEQUENCE gey_fact_seq
/
CREATE TABLE gey_fact (
  fact_id        NUMBER        CONSTRAINT gey_fact_pk PRIMARY KEY,
  thema          VARCHAR2(10)  NOT NULL CONSTRAINT gey_fact_thema_ck CHECK (thema IN ('GEYSIR', 'QUELLE', 'MINERAL', 'KLIMA', 'TIER', 'LICHT')),
  bezug          VARCHAR2(30 CHAR),
  text           VARCHAR2(400 CHAR) NOT NULL,
  wert           NUMBER,
  einheit        VARCHAR2(12 CHAR),
  reihenfolge    NUMBER(3),
  source_id      NUMBER        NOT NULL CONSTRAINT gey_fact_source_fk REFERENCES gey_source (source_id)
)
/
CREATE INDEX gey_fact_bezug_ix ON gey_fact (thema, bezug)
/
CREATE INDEX gey_fact_source_ix ON gey_fact (source_id)
/
CREATE OR REPLACE TRIGGER gey_fact_bi BEFORE INSERT ON gey_fact FOR EACH ROW
BEGIN
  IF :new.fact_id IS NULL THEN :new.fact_id := gey_fact_seq.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE gey_fact IS 'Geyser: Kennzahlen mit Beleg, wie sie auf Tafeln, in der Lupe und im Drehbuch stehen'
/

-- ------------------------------------------------------------- Orte, Geysire, Quellen
CREATE SEQUENCE gey_site_seq
/
CREATE TABLE gey_site (
  site_id        NUMBER        CONSTRAINT gey_site_pk PRIMARY KEY,
  code           VARCHAR2(30 CHAR)  NOT NULL CONSTRAINT gey_site_uk UNIQUE,
  name           VARCHAR2(60 CHAR)  NOT NULL,
  art            VARCHAR2(8)   NOT NULL CONSTRAINT gey_site_art_ck CHECK (art IN ('GEYSIR', 'QUELLE')),
  becken         VARCHAR2(8)   NOT NULL CONSTRAINT gey_site_becken_ck CHECK (becken IN ('UPPER', 'MIDWAY')),
  breite         NUMBER(10,7)  NOT NULL,
  laenge         NUMBER(11,7)  NOT NULL,
  hoehe_m        NUMBER(6,1),
  lage           MDSYS.SDO_GEOMETRY,
  saeule_max_m   NUMBER(5,1),
  tafel          VARCHAR2(200 CHAR),
  taste          NUMBER(1)     CONSTRAINT gey_site_taste_uk UNIQUE
)
/
CREATE OR REPLACE TRIGGER gey_site_bi BEFORE INSERT ON gey_site FOR EACH ROW
BEGIN
  IF :new.site_id IS NULL THEN :new.site_id := gey_site_seq.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE gey_site IS 'Geyser: die Stellen der Absteckung (Tasten 1 bis 7 und weitere), Punkt in SRID 8307'
/

CREATE SEQUENCE gey_geyser_seq
/
CREATE TABLE gey_geyser (
  geyser_id      NUMBER        CONSTRAINT gey_geyser_pk PRIMARY KEY,
  site_id        NUMBER        NOT NULL CONSTRAINT gey_geyser_site_fk REFERENCES gey_site (site_id)
                               CONSTRAINT gey_geyser_site_uk UNIQUE,
  typ            VARCHAR2(8)   NOT NULL CONSTRAINT gey_geyser_typ_ck CHECK (typ IN ('KEGEL', 'FONTAENE')),
  roehre_m       NUMBER(5,1)   NOT NULL,
  schlot_r_m     NUMBER(4,2)   NOT NULL,
  saeule_max_m   NUMBER(5,1)   NOT NULL,
  kurz_min_s     NUMBER(6),
  kurz_max_s     NUMBER(6),
  lang_min_s     NUMBER(6),
  lang_max_s     NUMBER(6),
  anteil_kurz    NUMBER(3,2)   CONSTRAINT gey_geyser_anteil_ck CHECK (anteil_kurz BETWEEN 0 AND 1),
  abstand_kurz_s NUMBER(7),
  abstand_lang_s NUMBER(7),
  streuung_s     NUMBER(7),
  dampfphase_s   NUMBER(6),
  neigung_grad   NUMBER(4,1),
  stoesse_min    NUMBER(2),
  stoesse_max    NUMBER(2)
)
/
CREATE OR REPLACE TRIGGER gey_geyser_bi BEFORE INSERT ON gey_geyser FOR EACH ROW
BEGIN
  IF :new.geyser_id IS NULL THEN :new.geyser_id := gey_geyser_seq.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE gey_geyser IS 'Geyser: Kennwerte der Röhrenmodelle (Sekunden, Meter)'
/

CREATE SEQUENCE gey_spring_seq
/
CREATE TABLE gey_spring (
  spring_id      NUMBER        CONSTRAINT gey_spring_pk PRIMARY KEY,
  code           VARCHAR2(30 CHAR)  NOT NULL CONSTRAINT gey_spring_uk UNIQUE,
  name           VARCHAR2(60 CHAR)  NOT NULL,
  art            VARCHAR2(8)   NOT NULL CONSTRAINT gey_spring_art_ck CHECK (art IN ('BECKEN', 'SCHLOT', 'KRATER')),
  site_id        NUMBER        CONSTRAINT gey_spring_site_fk REFERENCES gey_site (site_id),
  temp_c         NUMBER(4,1),
  abfall_k       NUMBER(4,1),
  halbachse_x_m  NUMBER(5,1),
  halbachse_z_m  NUMBER(5,1),
  tiefe_m        NUMBER(5,1),
  abfluss_m      NUMBER(5,1),
  saum_m         NUMBER(5,1),
  lage           MDSYS.SDO_GEOMETRY,
  genaehert      CHAR(1) DEFAULT 'N' NOT NULL CONSTRAINT gey_spring_gen_ck CHECK (genaehert IN ('J', 'N'))
)
/
CREATE INDEX gey_spring_site_ix ON gey_spring (site_id)
/
CREATE OR REPLACE TRIGGER gey_spring_bi BEFORE INSERT ON gey_spring FOR EACH ROW
BEGIN
  IF :new.spring_id IS NULL THEN :new.spring_id := gey_spring_seq.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE gey_spring IS 'Geyser: Quellen des Temperaturfelds (formen das Gelände, daher nur zum Nachschlagen)'
/

-- ------------------------------------------------------------- Mineralien, Klima
CREATE SEQUENCE gey_mineral_seq
/
CREATE TABLE gey_mineral (
  mineral_id     NUMBER        CONSTRAINT gey_mineral_pk PRIMARY KEY,
  code           VARCHAR2(30 CHAR)  NOT NULL CONSTRAINT gey_mineral_uk UNIQUE,
  name           VARCHAR2(60 CHAR)  NOT NULL,
  formel         VARCHAR2(40 CHAR)  NOT NULL,
  art            VARCHAR2(60 CHAR),
  hier           CHAR(1) DEFAULT 'N' NOT NULL CONSTRAINT gey_mineral_hier_ck CHECK (hier IN ('J', 'N')),
  ort            VARCHAR2(160 CHAR),
  text           VARCHAR2(1000 CHAR),
  farbe_hex      VARCHAR2(7)   CONSTRAINT gey_mineral_farbe_ck CHECK (REGEXP_LIKE(farbe_hex, '^#[0-9A-F]{6}$')),
  reihenfolge    NUMBER(2)
)
/
CREATE OR REPLACE TRIGGER gey_mineral_bi BEFORE INSERT ON gey_mineral FOR EACH ROW
BEGIN
  IF :new.mineral_id IS NULL THEN :new.mineral_id := gey_mineral_seq.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE gey_mineral IS 'Geyser: Ablagerungen der Mineral-Lupe; Kennzahlen in GEY_FACT (thema MINERAL, bezug = code)'
/

CREATE TABLE gey_mineral_element (
  mineral_id     NUMBER        NOT NULL CONSTRAINT gey_min_el_min_fk REFERENCES gey_mineral (mineral_id) ON DELETE CASCADE,
  atomic_number  NUMBER(3)     NOT NULL CONSTRAINT gey_min_el_z_ck CHECK (atomic_number BETWEEN 1 AND 118),
  anzahl         NUMBER(7,4),
  spur           CHAR(1) DEFAULT 'N' NOT NULL CONSTRAINT gey_min_el_spur_ck CHECK (spur IN ('J', 'N')),
  reihenfolge    NUMBER(2),
  CONSTRAINT gey_min_el_pk PRIMARY KEY (mineral_id, atomic_number),
  CONSTRAINT gey_min_el_anz_ck CHECK (spur = 'J' OR anzahl > 0)
)
/
COMMENT ON TABLE gey_mineral_element IS 'Geyser: Atome je Formeleinheit; Ordnungszahl ohne Fremdschlüssel, geprüft gegen am_element (GEY_MINERAL_CHECK_V)'
/

CREATE TABLE gey_climate (
  monat          NUMBER(2)     CONSTRAINT gey_climate_pk PRIMARY KEY CONSTRAINT gey_climate_monat_ck CHECK (monat BETWEEN 1 AND 12),
  hoch_f         NUMBER(4,1)   NOT NULL,
  tief_f         NUMBER(4,1)   NOT NULL,
  hoch_c         NUMBER GENERATED ALWAYS AS (ROUND((hoch_f - 32) / 1.8, 1)) VIRTUAL,
  tief_c         NUMBER GENERATED ALWAYS AS (ROUND((tief_f - 32) / 1.8, 1)) VIRTUAL,
  source_id      NUMBER        CONSTRAINT gey_climate_source_fk REFERENCES gey_source (source_id)
)
/
COMMENT ON TABLE gey_climate IS 'Geyser: Monatsnormalwerte 1991–2020 der Station Old Faithful (°F wie in der Quelle)'
/

-- ------------------------------------------------------------- Protokoll und Zustand
CREATE SEQUENCE gey_session_seq
/
CREATE TABLE gey_session (
  session_id     NUMBER        CONSTRAINT gey_session_pk PRIMARY KEY,
  beginn         TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
  ende           TIMESTAMP,
  rechner        VARCHAR2(64 CHAR),
  benutzer       VARCHAR2(64 CHAR),
  java           VARCHAR2(40 CHAR),
  aufloesung     VARCHAR2(20 CHAR),
  bilder         NUMBER(9),
  bilder_s       NUMBER(5,1)
)
/
CREATE OR REPLACE TRIGGER gey_session_bi BEFORE INSERT ON gey_session FOR EACH ROW
BEGIN
  IF :new.session_id IS NULL THEN :new.session_id := gey_session_seq.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE gey_session IS 'Geyser: eine Zeile je Start der App'
/

CREATE SEQUENCE gey_eruption_seq
/
CREATE TABLE gey_eruption (
  eruption_id    NUMBER        CONSTRAINT gey_eruption_pk PRIMARY KEY,
  session_id     NUMBER        NOT NULL CONSTRAINT gey_eruption_session_fk REFERENCES gey_session (session_id) ON DELETE CASCADE,
  geyser_id      NUMBER        NOT NULL CONSTRAINT gey_eruption_geyser_fk REFERENCES gey_geyser (geyser_id),
  szenentag      NUMBER(3)     CONSTRAINT gey_eruption_tag_ck CHECK (szenentag BETWEEN 1 AND 366),
  szenenzeit_h   NUMBER(6,4)   CONSTRAINT gey_eruption_zeit_ck CHECK (szenenzeit_h >= 0 AND szenenzeit_h < 24),
  beginn_s       NUMBER(12,1),
  dauer_s        NUMBER(7,1)   NOT NULL,
  hoehe_max_m    NUMBER(5,1),
  vorhersage_s   NUMBER(12,1),
  abstand_s      NUMBER(9,1),
  von_hand       CHAR(1) DEFAULT 'N' NOT NULL CONSTRAINT gey_eruption_hand_ck CHECK (von_hand IN ('J', 'N')),
  abweichung_min NUMBER GENERATED ALWAYS AS (ROUND((beginn_s - vorhersage_s) / 60, 2)) VIRTUAL,
  angelegt       TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)
/
CREATE INDEX gey_eruption_geyser_ix ON gey_eruption (geyser_id)
/
CREATE INDEX gey_eruption_session_ix ON gey_eruption (session_id)
/
CREATE OR REPLACE TRIGGER gey_eruption_bi BEFORE INSERT ON gey_eruption FOR EACH ROW
BEGIN
  IF :new.eruption_id IS NULL THEN :new.eruption_id := gey_eruption_seq.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE gey_eruption IS 'Geyser: jeder beendete Ausbruch mit Vorhersage und Abweichung (Minuten, virtuell)'
/

CREATE SEQUENCE gey_state_seq
/
CREATE TABLE gey_state (
  state_id       NUMBER        CONSTRAINT gey_state_pk PRIMARY KEY,
  name           VARCHAR2(60 CHAR)  NOT NULL CONSTRAINT gey_state_uk UNIQUE,
  tag            NUMBER(3)     CONSTRAINT gey_state_tag_ck CHECK (tag BETWEEN 1 AND 366),
  stunde         NUMBER(6,4)   CONSTRAINT gey_state_stunde_ck CHECK (stunde >= 0 AND stunde < 24),
  ort            NUMBER(1)     DEFAULT 0 NOT NULL CONSTRAINT gey_state_ort_ck CHECK (ort IN (0, 1)),
  dreh_x         NUMBER(9,2),
  dreh_y         NUMBER(9,2),
  dreh_z         NUMBER(9,2),
  gier_grad      NUMBER(9,2),
  nick_grad      NUMBER(9,2),
  abstand_m      NUMBER(9,2),
  dunst          NUMBER(3,2)   CONSTRAINT gey_state_dunst_ck CHECK (dunst BETWEEN 0 AND 1),
  wind           NUMBER(3,2)   CONSTRAINT gey_state_wind_ck CHECK (wind BETWEEN 0 AND 1),
  beschriftung   CHAR(1) DEFAULT 'J' NOT NULL CONSTRAINT gey_state_besch_ck CHECK (beschriftung IN ('J', 'N')),
  schnitt        CHAR(1) DEFAULT 'N' NOT NULL CONSTRAINT gey_state_schnitt_ck CHECK (schnitt IN ('J', 'N')),
  waermebild     CHAR(1) DEFAULT 'N' NOT NULL CONSTRAINT gey_state_waerme_ck CHECK (waermebild IN ('J', 'N')),
  tiere          CHAR(1) DEFAULT 'J' NOT NULL CONSTRAINT gey_state_tiere_ck CHECK (tiere IN ('J', 'N')),
  rechner        VARCHAR2(64 CHAR),
  gespeichert    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL
)
/
CREATE OR REPLACE TRIGGER gey_state_bi BEFORE INSERT ON gey_state FOR EACH ROW
BEGIN
  IF :new.state_id IS NULL THEN :new.state_id := gey_state_seq.NEXTVAL; END IF;
END;
/
COMMENT ON TABLE gey_state IS 'Geyser: benannte Zustände; „Beim Beenden“ schreibt die App selbst'
/
