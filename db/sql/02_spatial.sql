-- =====================================================================
-- Geyser · Phase 9 · 02 Spatial
-- Metadaten und Indizes (Indextyp V2) für die Punkte in GEY_SITE und
-- GEY_SPRING, SRID 8307 (WGS 84), Toleranz 5 cm.
-- =====================================================================

INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
VALUES ('GEY_SITE', 'LAGE',
        MDSYS.SDO_DIM_ARRAY(MDSYS.SDO_DIM_ELEMENT('Longitude', -180, 180, 0.05),
                            MDSYS.SDO_DIM_ELEMENT('Latitude', -90, 90, 0.05)), 8307)
/
INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
VALUES ('GEY_SPRING', 'LAGE',
        MDSYS.SDO_DIM_ARRAY(MDSYS.SDO_DIM_ELEMENT('Longitude', -180, 180, 0.05),
                            MDSYS.SDO_DIM_ELEMENT('Latitude', -90, 90, 0.05)), 8307)
/
COMMIT
/
CREATE INDEX gey_site_sx ON gey_site (lage) INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2 PARAMETERS ('layer_gtype=POINT')
/
CREATE INDEX gey_spring_sx ON gey_spring (lage) INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2 PARAMETERS ('layer_gtype=POINT')
/
