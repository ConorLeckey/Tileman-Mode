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
    public TilemanImportPanel(TilemanModePlugin plugin) {
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

        JLabel info = new JLabel(htmlLabel("Clicking the Import button below will migrate all tiles marked with the Ground Marker plugin into the Tileman Mode plugin. They will NOT be removed from the Ground Marker Plugin.", "#FFFFFF"));

        JLabel warning = new JLabel(htmlLabel("WARNING: This directly modifies RuneLite's settings.properties file. You should make a back up before importing.", "#FFFF00"));

        infoPanel.add(info);
        infoPanel.add(warning);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JButton importButton = new JButton("Import");
        centerPanel.add(importButton, BorderLayout.SOUTH);
        importButton.addActionListener(l -> plugin.importGroundMarkerTiles());

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


    private static String htmlLabel(String key, String color)
    {
        return "<html><body style = 'color:" + color + "'>" + key + "</body></html>";
    }
}