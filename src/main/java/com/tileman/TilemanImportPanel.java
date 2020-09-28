package com.tileman;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

@Slf4j
@Singleton
public class TilemanImportPanel extends PluginPanel {
    private final TilemanModePlugin plugin;

    public TilemanImportPanel(TilemanModePlugin plugin) {
        this.plugin = plugin;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.setBorder(new EmptyBorder(1, 0, 10, 0));

        JLabel title = new JLabel();
        title.setText("Tileman Mode Import Panel");
        title.setForeground(Color.WHITE);

        northPanel.add(title, BorderLayout.NORTH);

        JPanel infoPanel = new JPanel();
        infoPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        infoPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        infoPanel.setLayout(new GridLayout(0, 1));

        JLabel warning = new JLabel(htmlLabel("WARNING: Clicking the Import button below will migrate all Ground Marker Tiles into Tileman Mode Tiles. They will NOT be removed from the Ground Marker Plugin."));

        infoPanel.add(warning);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JButton importButton = new JButton("Import");
        centerPanel.add(importButton, BorderLayout.SOUTH);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.gridx = 0;
        constraints.gridy = 0;

        importButton.setToolTipText("Import Ground Markers");

        add(northPanel, BorderLayout.NORTH);
        add(infoPanel, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.SOUTH);
    }


    private static String htmlLabel(String key)
    {
        return "<html><body style = 'color:#FFFF00'>" + key + "</body></html>";
    }
}