package com.dan.geyser.tools;

import com.dan.geyser.db.DbConfig;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

/**
 * Bestandsaufnahme im Schema aus <code>db/db.properties</code>, nur lesend: Präfixe der vorhandenen
 * Tabellen, Objekte mit GEY_, Spatial-Metadaten, Platz und Rechte, Zugriff auf das ATOMMODEL. Schreibt nach
 * <code>db/bestand.txt</code> im Projektordner. Ändert nichts.
 * <p>In NetBeans: Rechtsklick auf die Datei, „Run File“. Arbeitsordner ist der Projektordner.</p>
 */
public final class DbBestand {
    public static void main(String[] a) throws Exception {
        DbConfig cfg = DbConfig.load();
        if (cfg == null) {
            System.out.println("db/db.properties fehlt oder ist unvollständig (url, user, password). Vorlage: db/LIESMICH.txt");
            return;
        }
        File out = new File("db/bestand.txt");
        out.getParentFile().mkdirs();
        try (Connection c = DriverManager.getConnection(cfg.url, cfg.user, cfg.password);
             PrintWriter w = new PrintWriter(out, StandardCharsets.UTF_8)) {
            c.setReadOnly(true);
            w.println("Geyser · Bestandsaufnahme · " + cfg.user + " @ " + cfg.url + " · " + java.time.LocalDateTime.now().withNano(0));
            w.println();
            q(c, w, "Datenbank", "select banner_full from v$version");
            q(c, w, "Sitzung", "select user, sys_context('USERENV','CON_NAME') pdb, sys_context('USERENV','DB_NAME') db, "
                    + "(select value from nls_database_parameters where parameter='NLS_CHARACTERSET') zeichensatz, "
                    + "(select value from nls_session_parameters where parameter='NLS_LENGTH_SEMANTICS') laenge, to_char(sysdate,'YYYY-MM-DD HH24:MI') jetzt from dual");
            q(c, w, "Tablespace und Quote", "select u.default_tablespace, q.tablespace_name, round(q.bytes/1048576) mb_belegt, "
                    + "case q.max_bytes when -1 then 'unbegrenzt' else to_char(round(q.max_bytes/1048576)) end mb_max "
                    + "from user_users u left join user_ts_quotas q on 1=1");
            q(c, w, "Tabellen nach Präfix", "select nvl(regexp_substr(table_name,'^[A-Z0-9]+_'),'(ohne)') praefix, count(*) tabellen, "
                    + "sum(nvl(num_rows,0)) zeilen_statistik from user_tables group by nvl(regexp_substr(table_name,'^[A-Z0-9]+_'),'(ohne)') order by 1");
            q(c, w, "Objekte nach Typ", "select object_type, count(*) anzahl, sum(case when status<>'VALID' then 1 else 0 end) ungueltig "
                    + "from user_objects group by object_type order by 1");
            q(c, w, "Alles mit GEY_ (vor der Einrichtung leer)", "select object_type, object_name, status from user_objects "
                    + "where object_name like 'GEY\\_%' escape '\\' order by 1,2");
            q(c, w, "Zeilen in GEY_-Tabellen (Statistik)", "select table_name, num_rows, last_analyzed from user_tables where table_name like 'GEY\\_%' escape '\\' order by 1");
            q(c, w, "Spatial-Metadaten nach Präfix", "select nvl(regexp_substr(table_name,'^[A-Z0-9]+_'),'(ohne)') praefix, count(*) eintraege, "
                    + "min(srid) srid_min, max(srid) srid_max from user_sdo_geom_metadata group by nvl(regexp_substr(table_name,'^[A-Z0-9]+_'),'(ohne)') order by 1");
            q(c, w, "Spatial vorhanden", "select count(*) sdo_geometry_typ from all_types where owner='MDSYS' and type_name='SDO_GEOMETRY'");
            q(c, w, "Spatial-Indextyp V2", "select count(*) spatial_index_v2 from all_indextypes where owner='MDSYS' and indextype_name='SPATIAL_INDEX_V2'");
            q(c, w, "Rechte der Sitzung", "select privilege from session_privs order by 1");
            q(c, w, "Rollen", "select granted_role from user_role_privs order by 1");
            q(c, w, "ATOMMODEL: Version der API", "select am_api.version() version from dual");
            q(c, w, "ATOMMODEL: Elemente der Mineral-Lupe", "select atomic_number, symbol, name_de, atomic_mass, neutrons, shell_config "
                    + "from am_element where atomic_number in (1,6,8,13,14,16,19,20,26,33,51) order by 1");
            q(c, w, "ATOMMODEL: öffentliche Synonyme", "select synonym_name, table_owner, table_name from all_synonyms "
                    + "where owner='PUBLIC' and table_owner='ATOMMODEL' order by 1");
        }
        System.out.println("Geschrieben: " + out.getAbsolutePath());
    }

    private static void q(Connection c, PrintWriter w, String title, String sql) {
        w.println("== " + title);
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            ResultSetMetaData md = r.getMetaData();
            int n = md.getColumnCount();
            StringBuilder h = new StringBuilder();
            for (int i = 1; i <= n; i++) h.append(i > 1 ? " | " : "").append(md.getColumnLabel(i));
            w.println(h);
            int rows = 0;
            while (r.next() && rows < 400) {
                StringBuilder b = new StringBuilder();
                for (int i = 1; i <= n; i++) b.append(i > 1 ? " | " : "").append(r.getString(i));
                w.println(b);
                rows++;
            }
            if (rows == 0) w.println("(keine Zeilen)");
        } catch (Exception e) {
            w.println("(nicht lesbar: " + String.valueOf(e.getMessage()).trim() + ")");
        }
        w.println();
    }

    private DbBestand() { }
}
