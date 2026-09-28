package com.dan.geyser.ui;

import com.dan.fDialog.FDialog;
import com.dan.fbutton.FButton;
import com.dan.foptionpane.FOptionPane;
import com.dan.ftable.FTable;
import com.dan.ftable.FTableDensity;
import com.dan.ftable.FTableStyle;
import com.dan.geyser.db.State;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;

/**
 * Benannte Zustände in der Datenbank: speichern unter einem Namen, laden und löschen. Eingaben über
 * FOptionPane und FDialog aus FStyle. „Beim Beenden“ schreibt die App selbst.
 */
final class StateDialogs {
    private StateDialogs() { }

    static void saveAs(Component parent, ScenePanel scene) {
        if (!scene.db().ready()) {
            FOptionPane.showMessageDialog(parent, "Zustände unter einem Namen braucht die Datenbank.\n" + scene.db().status()
                    + "\n\nOhne Datenbank merkt sich die App nur den Zustand beim Beenden (Datei im Benutzerordner).", "Zustand", FOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String name = FOptionPane.showInputDialog(parent, "Name für den Zustand (Tag, Uhrzeit, Ort, Kamera, Luft, Zugaben):", "Zustand speichern unter …",
                FOptionPane.QUESTION_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        name = name.trim();
        if (name.length() > 60) name = name.substring(0, 60);
        if (name.equals(State.LAST)) {
            FOptionPane.showMessageDialog(parent, "„" + State.LAST + "“ ist für den Zustand beim Beenden reserviert.", "Zustand", FOptionPane.WARNING_MESSAGE);
            return;
        }
        State s = scene.captureState();
        s.name = name;
        String n = name;
        scene.db().saveState(s, err -> SwingUtilities.invokeLater(() -> FOptionPane.showMessageDialog(parent,
                err == null ? "Gespeichert: „" + n + "“\n" + s.summary() : "Nicht gespeichert: " + err, "Zustand",
                err == null ? FOptionPane.INFORMATION_MESSAGE : FOptionPane.ERROR_MESSAGE)));
    }

    static void load(Component parent, ScenePanel scene) {
        if (!scene.db().ready()) {
            FOptionPane.showMessageDialog(parent, "Gespeicherte Zustände braucht die Datenbank.\n" + scene.db().status(), "Zustand", FOptionPane.INFORMATION_MESSAGE);
            return;
        }
        scene.db().states(list -> SwingUtilities.invokeLater(() -> show(parent, scene, list)),
                err -> SwingUtilities.invokeLater(() -> FOptionPane.showMessageDialog(parent, "Nicht lesbar: " + err, "Zustand", FOptionPane.ERROR_MESSAGE)));
    }

    private static void show(Component parent, ScenePanel scene, List<State> list) {
        FDialog d = new FDialog(SwingUtilities.getWindowAncestor(parent), "Zustand laden", true);
        JPanel p = d.getComponentPane();
        p.setLayout(new BorderLayout(0, 10));
        p.setBackground(ControlPanel.BG);
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        String[] cols = {"Name", "Tag und Uhrzeit", "Ort", "Zugaben", "Rechner", "Gespeichert"};
        java.text.SimpleDateFormat sf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm");
        Object[][] rows = new Object[list.size()][];
        for (int i = 0; i < list.size(); i++) {
            State s = list.get(i);
            String z = (s.tube ? "Schnitt " : "") + (s.thermo ? "Wärmebild " : "") + (s.fauna ? "Tiere" : "");
            rows[i] = new Object[]{s.name, com.dan.geyser.effects.DayNightCycle.dateLabel(s.day) + ", " + com.dan.geyser.effects.DayNightCycle.timeLabel(s.hour),
                    s.site == 1 ? "Midway" : s.site == 2 ? "Lower" : "Upper", z.trim().isEmpty() ? "–" : z.trim(), s.host == null ? "" : s.host,
                    s.saved == null ? "" : sf.format(s.saved)};
        }
        FTable t = new FTable();
        t.setStyle(FTableStyle.LAB_DARK);
        t.setDensity(FTableDensity.COMPACT);
        t.setData(cols, rows);
        if (!list.isEmpty()) t.setRowSelectionInterval(0, 0);
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(ControlPanel.BG);
        sp.setVerticalScrollBar(new com.dan.fscrollbar.FScrollBar(javax.swing.JScrollBar.VERTICAL));
        p.add(sp, BorderLayout.CENTER);
        JPanel btn = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btn.setOpaque(false);
        FButton ok = new FButton("Laden"), del = new FButton("Löschen"), close = new FButton("Schließen");
        ok.addActionListener(e -> {
            int r = t.getSelectedRow();
            if (r < 0) return;
            scene.applyState(list.get(t.convertRowIndexToModel(r)));
            d.dispose();
        });
        del.addActionListener(e -> {
            int r = t.getSelectedRow();
            if (r < 0) return;
            State s = list.get(t.convertRowIndexToModel(r));
            if (FOptionPane.showConfirmDialog(d, "„" + s.name + "“ löschen?", "Zustand", FOptionPane.YES_NO_OPTION) != FOptionPane.YES_OPTION) return;
            scene.db().deleteState(s.name, err -> SwingUtilities.invokeLater(() -> {
                d.dispose();
                if (err != null) FOptionPane.showMessageDialog(parent, "Nicht gelöscht: " + err, "Zustand", FOptionPane.ERROR_MESSAGE);
                else load(parent, scene);
            }));
        });
        close.addActionListener(e -> d.dispose());
        btn.add(del);
        btn.add(ok);
        btn.add(close);
        p.add(btn, BorderLayout.SOUTH);
        d.setPreferredDialogSize(new Dimension(820, 460));
        d.setResizable(true);
        d.pack();
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
    }
}
