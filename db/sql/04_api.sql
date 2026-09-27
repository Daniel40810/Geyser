-- =====================================================================
-- Geyser · Phase 9 · 04 Package GEY_API und Sichten
-- Die App schreibt nur über GEY_API (Sitzung, Ausbruch, Zustand) und
-- liest Stammdaten, Zustände und Auswertungen per SQL und Sicht.
-- Das Package schreibt, bestätigt aber nicht: das macht der Aufrufer.
-- =====================================================================

CREATE OR REPLACE PACKAGE gey_api AUTHID DEFINER AS
  FUNCTION version RETURN VARCHAR2;

  FUNCTION session_begin(p_rechner VARCHAR2, p_benutzer VARCHAR2, p_java VARCHAR2, p_aufloesung VARCHAR2) RETURN NUMBER;

  PROCEDURE session_end(p_session_id NUMBER, p_bilder NUMBER, p_bilder_s NUMBER, p_aufloesung VARCHAR2 DEFAULT NULL);

  -- Geysir per Code der Stelle (OLD_FAITHFUL …); liefert die neue eruption_id
  FUNCTION log_eruption(p_session_id NUMBER, p_geysir VARCHAR2, p_tag NUMBER, p_zeit_h NUMBER,
                        p_beginn_s NUMBER, p_dauer_s NUMBER, p_hoehe_m NUMBER,
                        p_vorhersage_s NUMBER, p_abstand_s NUMBER, p_von_hand VARCHAR2) RETURN NUMBER;

  -- legt an oder ersetzt (per Name)
  PROCEDURE save_state(p_name VARCHAR2, p_tag NUMBER, p_stunde NUMBER, p_ort NUMBER,
                       p_dreh_x NUMBER, p_dreh_y NUMBER, p_dreh_z NUMBER,
                       p_gier NUMBER, p_nick NUMBER, p_abstand NUMBER,
                       p_dunst NUMBER, p_wind NUMBER,
                       p_beschriftung VARCHAR2, p_schnitt VARCHAR2, p_waermebild VARCHAR2, p_tiere VARCHAR2,
                       p_rechner VARCHAR2);

  PROCEDURE delete_state(p_name VARCHAR2);
END gey_api;
/

CREATE OR REPLACE PACKAGE BODY gey_api AS
  FUNCTION version RETURN VARCHAR2 IS
  BEGIN
    RETURN 'GEY_API 1.0 (2026-09-27)';
  END version;

  FUNCTION session_begin(p_rechner VARCHAR2, p_benutzer VARCHAR2, p_java VARCHAR2, p_aufloesung VARCHAR2) RETURN NUMBER IS
    v_id NUMBER;
  BEGIN
    INSERT INTO gey_session (rechner, benutzer, java, aufloesung)
    VALUES (SUBSTR(p_rechner, 1, 64), SUBSTR(p_benutzer, 1, 64), SUBSTR(p_java, 1, 40), SUBSTR(p_aufloesung, 1, 20))
    RETURNING session_id INTO v_id;
    RETURN v_id;
  END session_begin;

  PROCEDURE session_end(p_session_id NUMBER, p_bilder NUMBER, p_bilder_s NUMBER, p_aufloesung VARCHAR2 DEFAULT NULL) IS
  BEGIN
    UPDATE gey_session
       SET ende = SYSTIMESTAMP, bilder = p_bilder, bilder_s = ROUND(p_bilder_s, 1),
           aufloesung = NVL(SUBSTR(p_aufloesung, 1, 20), aufloesung)
     WHERE session_id = p_session_id;
  END session_end;

  FUNCTION log_eruption(p_session_id NUMBER, p_geysir VARCHAR2, p_tag NUMBER, p_zeit_h NUMBER,
                        p_beginn_s NUMBER, p_dauer_s NUMBER, p_hoehe_m NUMBER,
                        p_vorhersage_s NUMBER, p_abstand_s NUMBER, p_von_hand VARCHAR2) RETURN NUMBER IS
    v_geyser NUMBER;
    v_id     NUMBER;
  BEGIN
    SELECT g.geyser_id INTO v_geyser
      FROM gey_geyser g JOIN gey_site s ON s.site_id = g.site_id
     WHERE s.code = p_geysir;
    INSERT INTO gey_eruption (session_id, geyser_id, szenentag, szenenzeit_h, beginn_s, dauer_s, hoehe_max_m,
                              vorhersage_s, abstand_s, von_hand)
    VALUES (p_session_id, v_geyser, p_tag, p_zeit_h, p_beginn_s, p_dauer_s, p_hoehe_m,
            p_vorhersage_s, p_abstand_s, CASE WHEN p_von_hand = 'J' THEN 'J' ELSE 'N' END)
    RETURNING eruption_id INTO v_id;
    RETURN v_id;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      raise_application_error(-20902, 'Unbekannter Geysir: ' || p_geysir);
  END log_eruption;

  PROCEDURE save_state(p_name VARCHAR2, p_tag NUMBER, p_stunde NUMBER, p_ort NUMBER,
                       p_dreh_x NUMBER, p_dreh_y NUMBER, p_dreh_z NUMBER,
                       p_gier NUMBER, p_nick NUMBER, p_abstand NUMBER,
                       p_dunst NUMBER, p_wind NUMBER,
                       p_beschriftung VARCHAR2, p_schnitt VARCHAR2, p_waermebild VARCHAR2, p_tiere VARCHAR2,
                       p_rechner VARCHAR2) IS
  BEGIN
    MERGE INTO gey_state s
    USING (SELECT p_name AS name FROM dual) q ON (s.name = q.name)
    WHEN MATCHED THEN UPDATE SET
         tag = p_tag, stunde = p_stunde, ort = p_ort, dreh_x = p_dreh_x, dreh_y = p_dreh_y, dreh_z = p_dreh_z,
         gier_grad = p_gier, nick_grad = p_nick, abstand_m = p_abstand, dunst = p_dunst, wind = p_wind,
         beschriftung = p_beschriftung, schnitt = p_schnitt, waermebild = p_waermebild, tiere = p_tiere,
         rechner = SUBSTR(p_rechner, 1, 64), gespeichert = SYSTIMESTAMP
    WHEN NOT MATCHED THEN INSERT
         (name, tag, stunde, ort, dreh_x, dreh_y, dreh_z, gier_grad, nick_grad, abstand_m, dunst, wind,
          beschriftung, schnitt, waermebild, tiere, rechner)
         VALUES (p_name, p_tag, p_stunde, p_ort, p_dreh_x, p_dreh_y, p_dreh_z, p_gier, p_nick, p_abstand, p_dunst, p_wind,
                 p_beschriftung, p_schnitt, p_waermebild, p_tiere, SUBSTR(p_rechner, 1, 64));
  END save_state;

  PROCEDURE delete_state(p_name VARCHAR2) IS
  BEGIN
    DELETE FROM gey_state WHERE name = p_name;
  END delete_state;
END gey_api;
/

-- ------------------------------------------------------------- Sichten
CREATE OR REPLACE VIEW gey_site_v AS
SELECT s.site_id, s.code, s.name, s.art, s.becken, s.breite, s.laenge, s.hoehe_m, s.saeule_max_m, s.tafel, s.taste, s.lage,
       ROUND(MDSYS.SDO_GEOM.SDO_DISTANCE(s.lage, o.lage, 0.05, 'unit=M')) AS abstand_of_m
  FROM gey_site s
 CROSS JOIN (SELECT lage FROM gey_site WHERE code = 'OLD_FAITHFUL') o
/
COMMENT ON TABLE gey_site_v IS 'Geyser: Stellen mit Abstand zu Old Faithful in Metern'
/

CREATE OR REPLACE VIEW gey_eruption_v AS
SELECT e.eruption_id, e.session_id, ses.beginn AS sitzung_beginn, s.code AS geysir_code, s.name AS geysir,
       e.szenentag, e.szenenzeit_h, e.beginn_s, e.dauer_s, e.hoehe_max_m, e.vorhersage_s, e.abstand_s,
       e.von_hand, e.abweichung_min, e.angelegt
  FROM gey_eruption e
  JOIN gey_session ses ON ses.session_id = e.session_id
  JOIN gey_geyser g ON g.geyser_id = e.geyser_id
  JOIN gey_site s ON s.site_id = g.site_id
/
COMMENT ON TABLE gey_eruption_v IS 'Geyser: Ausbrüche mit Geysir, Sitzung und Abweichung der Vorhersage'
/

CREATE OR REPLACE VIEW gey_prediction_v AS
SELECT s.code AS geysir_code, s.name AS geysir,
       COUNT(e.eruption_id) AS ausbrueche,
       COUNT(e.vorhersage_s) AS mit_vorhersage,
       ROUND(AVG(ABS(e.abweichung_min)), 1) AS mittel_abw_min,
       ROUND(100 * SUM(CASE WHEN ABS(e.abweichung_min) <= 10 THEN 1 ELSE 0 END) / NULLIF(COUNT(e.vorhersage_s), 0)) AS anteil_10min_proz,
       ROUND(MIN(e.abstand_s) / 60, 1) AS abstand_min_min,
       ROUND(MAX(e.abstand_s) / 60, 1) AS abstand_max_min
  FROM gey_geyser g
  JOIN gey_site s ON s.site_id = g.site_id
  LEFT JOIN gey_eruption e ON e.geyser_id = g.geyser_id
 GROUP BY s.code, s.name
/
COMMENT ON TABLE gey_prediction_v IS 'Geyser: Güte der Vorhersage je Geysir über alle Sitzungen (von Hand ausgelöste ohne Vorhersage)'
/

CREATE OR REPLACE VIEW gey_mineral_check_v AS
SELECT m.code AS mineral, me.atomic_number, me.anzahl, me.spur, me.reihenfolge,
       e.symbol, e.name_de,
       CASE WHEN e.atomic_number IS NULL THEN 'FEHLT' ELSE 'OK' END AS pruefung
  FROM gey_mineral_element me
  JOIN gey_mineral m ON m.mineral_id = me.mineral_id
  LEFT JOIN am_element e ON e.atomic_number = me.atomic_number
/
COMMENT ON TABLE gey_mineral_check_v IS 'Geyser: Mineral-Elemente gegen am_element (ATOMMODEL); FEHLT wäre ein Fehler'
/
