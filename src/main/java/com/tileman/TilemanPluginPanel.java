package com.tileman;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.NumberFormat;

@Slf4j
@Singleton
public class TilemanPluginPanel extends PluginPanel {

    private static final int MIN_EXP_PER_TILE = 500;
    private static final int MAX_EXP_PER_TILE = 100000;

    private static final int MIN_TILE_OFFSET = 0;
    private static final int MAX_TILE_OFFSET = 50000;

    private final TilemanModePlugin plugin;
    private final TilemanProfileManager profileManager;

    public TilemanPluginPanel(TilemanModePlugin plugin, TilemanProfileManager profileManager) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        build();
    }

    public void rebuild() {
        SwingUtilities.invokeLater(() -> build());
    }

    private void build() {
        TilemanGameRules gameRules = profileManager.getGameRules();

        this.removeAll();
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        {
            JPanel northPanel = new JPanel(new BorderLayout());
            northPanel.setBorder(new EmptyBorder(1, 0, 10, 0));
            {
                JLabel title = new JLabel();
                title.setText("Tileman Mode");
                title.setForeground(Color.WHITE);
                northPanel.add(title, BorderLayout.NORTH);
            }

            JPanel gameRulesPanel = new JPanel();
            addVerticalLayout(gameRulesPanel);
            gameRulesPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
            {
                JPanel gameModeSelectPanel = new JPanel();
                addHorizontalLayout(gameModeSelectPanel);
                {
                    JLabel gameModeSelectLabel = new JLabel("Game Mode");

                    JComboBox gameModeSelect = new JComboBox(TilemanGameMode.values());
                    gameModeSelect.setSelectedItem(gameRules.gameMode);
                    gameModeSelect.addActionListener(l -> profileManager.setGameMode((TilemanGameMode)gameModeSelect.getSelectedItem()));

                    gameModeSelectPanel.add(gameModeSelectLabel);
                    gameModeSelectPanel.add(gameModeSelect);
                }


                JCheckBox customGameMode = new JCheckBox("Enable Custom Game Mode");
                customGameMode.setSelected(gameRules.enableCustomGameMode);
                customGameMode.addActionListener(l -> profileManager.setCustomGameMode(customGameMode.isSelected()));

                // spacer

                JCheckBox allowTileDeficit = new JCheckBox("Allow Tile Deficit");
                allowTileDeficit.setSelected(gameRules.allowTileDeficit);
                allowTileDeficit.addActionListener(l -> profileManager.setAllowTileDeficit(allowTileDeficit.isSelected()));

                JCheckBox includeTotalLevel = new JCheckBox("Tiles From Total Level");
                includeTotalLevel.setSelected(gameRules.includeTotalLevel);
                includeTotalLevel.addActionListener(l -> profileManager.setIncludeTotalLevel(includeTotalLevel.isSelected()));

                JCheckBox excludeExp = new JCheckBox("Exclude XP Tiles");
                excludeExp.setSelected(gameRules.excludeExp);
                excludeExp.addActionListener(l -> profileManager.setExcludeExp(excludeExp.isSelected()));

                JPanel tileOffsetPanel = new JPanel();
                addHorizontalLayout(tileOffsetPanel);
                {
                    JLabel tileOffsetLabel = new JLabel("Tile Offset");

                    SpinnerNumberModel numberModel = new SpinnerNumberModel(gameRules.tilesOffset, MIN_TILE_OFFSET, MAX_TILE_OFFSET, 1);
                    JSpinner tilesOffsetSpinner = new JSpinner(numberModel);
                    tilesOffsetSpinner.addChangeListener(l -> profileManager.setTileOffset(numberModel.getNumber().intValue()));

                    tileOffsetPanel.add(tileOffsetLabel);
                    tileOffsetPanel.add(tilesOffsetSpinner);
                }

                JPanel xpPanel = new JPanel();
                addHorizontalLayout(xpPanel);
                {
                    JLabel expPerTileLabel = new JLabel("Exp Per Tile");

                    SpinnerNumberModel numberModel = new SpinnerNumberModel(gameRules.expPerTile, MIN_EXP_PER_TILE, MAX_EXP_PER_TILE, 1);
                    JSpinner expPerTileSpinner = new JSpinner(numberModel);
                    expPerTileSpinner.addChangeListener(l -> profileManager.setExpPerTile(numberModel.getNumber().intValue()));

                    xpPanel.add(expPerTileLabel);
                    xpPanel.add(expPerTileSpinner);
                }

                gameRulesPanel.add(gameModeSelectPanel);
                gameRulesPanel.add(customGameMode);
                gameRulesPanel.add(allowTileDeficit);
                gameRulesPanel.add(includeTotalLevel);
                gameRulesPanel.add(excludeExp);
                gameRulesPanel.add(tileOffsetPanel);
                gameRulesPanel.add(xpPanel);
            }

            JPanel importPanel = new JPanel();
            {
                importPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                importPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
                importPanel.setLayout(new BoxLayout(importPanel, BoxLayout.PAGE_AXIS));

                JLabel info = new JLabel(htmlLabel("Clicking the Import button below will migrate all tiles marked with the Ground Marker plugin into the Tileman Mode plugin. They will NOT be removed from the Ground Marker Plugin.", "#FFFFFF"));
                JLabel warning = new JLabel(htmlLabel("WARNING: This directly modifies RuneLite's settings.properties file. You should make a back up before importing.", "#FFFF00"));

                JButton importButton = new JButton("Import");
                importButton.addActionListener(l -> plugin.importGroundMarkerTiles());
                importButton.setToolTipText("Import Ground Markers");

                importPanel.add(info);
                importPanel.add(warning);
                importPanel.add(importButton);
            }

            add(northPanel, BorderLayout.NORTH);
            add(gameRulesPanel, BorderLayout.CENTER);
            add(importPanel, BorderLayout.SOUTH);

        }

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.gridx = 0;
        constraints.gridy = 0;
    }

    private static String htmlLabel(String key, String color)
    {
        return "<html><body style = 'color:" + color + "'>" + key + "</body></html>";
    }

    private static void addHorizontalLayout(JPanel element) {
        element.setLayout(new BoxLayout(element, BoxLayout.X_AXIS));
    }

    private static void addVerticalLayout(JPanel element) {
        element.setLayout(new BoxLayout(element, BoxLayout.PAGE_AXIS));
    }
}