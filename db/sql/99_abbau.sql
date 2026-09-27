-- =====================================================================
-- Geyser · Phase 9 · 99 Abbau
-- Entfernt alles mit GEY_ (Sichten, Package, Tabellen samt Triggern und
-- Indizes, Sequenzen) und die Spatial-Metadaten mit GEY_. Sonst nichts.
-- =====================================================================
BEGIN
  FOR o IN (SELECT object_name, object_type FROM user_objects
             WHERE object_name LIKE 'GEY\_%' ESCAPE '\' AND object_type IN ('VIEW', 'PACKAGE')
             ORDER BY object_type) LOOP
    EXECUTE IMMEDIATE 'DROP ' || o.object_type || ' ' || o.object_name;
  END LOOP;
  FOR t IN (SELECT table_name FROM user_tables
             WHERE table_name IN ('GEY_ERUPTION', 'GEY_SESSION', 'GEY_STATE', 'GEY_MINERAL_ELEMENT', 'GEY_MINERAL',
                                  'GEY_CLIMATE', 'GEY_FACT', 'GEY_SPRING', 'GEY_GEYSER', 'GEY_SITE', 'GEY_SOURCE')
             ORDER BY DECODE(table_name, 'GEY_ERUPTION', 1, 'GEY_SESSION', 2, 'GEY_STATE', 3, 'GEY_MINERAL_ELEMENT', 4,
                                         'GEY_MINERAL', 5, 'GEY_CLIMATE', 6, 'GEY_FACT', 7, 'GEY_SPRING', 8,
                                         'GEY_GEYSER', 9, 'GEY_SITE', 10, 11)) LOOP
    EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS PURGE';
  END LOOP;
  FOR s IN (SELECT sequence_name FROM user_sequences WHERE sequence_name LIKE 'GEY\_%' ESCAPE '\') LOOP
    EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.sequence_name;
  END LOOP;
  DELETE FROM user_sdo_geom_metadata WHERE table_name LIKE 'GEY\_%' ESCAPE '\';
  COMMIT;
END;
/
