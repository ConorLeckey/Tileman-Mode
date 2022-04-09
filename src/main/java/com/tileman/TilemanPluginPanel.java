package com.tileman;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Slf4j
@Singleton
public class TilemanPluginPanel extends PluginPanel {

    private static final int MIN_EXP_PER_TILE = 500;
    private static final int MAX_EXP_PER_TILE = 100000;

    private static final int MIN_TILE_OFFSET = 0;
    private static final int MAX_TILE_OFFSET = 50000;

    private final TilemanModePlugin plugin;
    private final TilemanProfileManager profileManager;
    private final Client client;

    public TilemanPluginPanel(TilemanModePlugin plugin, Client client, TilemanProfileManager profileManager) {
        this.plugin = plugin;
        this.client = client;
        this.profileManager = profileManager;
        build();
    }

    public void rebuild() {
        SwingUtilities.invokeLater(() -> {
            build();
            validate();
        });
    }

    private void build() {
        this.removeAll();
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        {
            JPanel titlePanel = new JPanel(new BorderLayout());
            titlePanel.setBorder(new EmptyBorder(1, 0, 10, 0));
            {
                JLabel title = new JLabel();
                title.setText("Tileman Mode");
                title.setForeground(Color.WHITE);
                titlePanel.add(title, BorderLayout.NORTH);
            }

            JPanel bodyPanel = new JPanel();
            addVerticalLayout(bodyPanel);
            {
                JPanel profilePanel = buildProfilePanel();
                JPanel gameRulesPanel = buildGameRulesPanel();

                bodyPanel.add(profilePanel);
                bodyPanel.add(gameRulesPanel);
            }

            add(titlePanel, BorderLayout.NORTH);
            add(bodyPanel, BorderLayout.CENTER);
        }
    }

    private JPanel buildProfilePanel() {
        TilemanProfile activeProfile = profileManager.getActiveProfile();
        boolean isLoggedIn = plugin.isLoggedIn();

        JPanel profilePanel = new JPanel();
        addVerticalLayout(profilePanel);
        {
            //JLabel profileSelectLabel = new JLabel("Select a profile:");
            //profileSelectLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            //profilePanel.add(profileSelectLabel, BorderLayout.WEST);

            JLabel profileLabel = new JLabel();
            profileLabel.setAlignmentX(CENTER_ALIGNMENT);

            addSpacer(profilePanel);

            if (!isLoggedIn) {
                profileLabel.setText("Login to start");
            } else {
                if (activeProfile != TilemanProfile.NONE) {
                    profileLabel.setText(activeProfile.getProfileName());
                } else {
                    profileLabel.setText("Create a profile to start");
                }
            }
            profilePanel.add(profileLabel);

            if (activeProfile == TilemanProfile.NONE && isLoggedIn) {
                JButton createProfileButton = new JButton("Create");
                createProfileButton.setAlignmentX(CENTER_ALIGNMENT);
                createProfileButton.addActionListener(l -> {
                    String profileName = JOptionPane.showInputDialog(null, "Profile name:", client.getLocalPlayer().getName());
                    TilemanProfile profile = TilemanProfile.NONE;

                    Object[] options = new Object[] {"No, Fresh Profile", "Import Old Tile Data", "Import Ground Marker Data"};
                    int choice = JOptionPane.showOptionDialog(null, "Do you want to import existing tile data into this profile?", "New Profile", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, null);
                    if (choice == 0) {
                        profile = profileManager.createProfile(profileName);
                    } else if (choice == 1) {
                        profile = profileManager.createProfileWithLegacyData(profileName);
                    } else if (choice == 2) {
                        profile = profileManager.createProfileWithGroundMarkerData(profileName);
                    }
                    profileManager.setActiveProfile(profile);
                });
                profilePanel.add(createProfileButton);
            }
            addSpacer(profilePanel);
        }
        return profilePanel;
    }

    private JPanel buildProfileSelectionPanel() {
        TilemanProfile activeProfile = profileManager.getActiveProfile();

        JPanel profileSelectionPanel = new JPanel();
        addFlowLayout(profileSelectionPanel);
        {
            JComboBox profileSelect = new JComboBox();
            //List<TilemanProfile> profiles = profileManager.getProfiles();
            //profiles.add(TilemanProfile.NONE);
            //for (TilemanProfile profile : profiles) {
            //    profileSelect.addItem(profile);
            //}
            profileSelect.setSelectedItem(activeProfile);
            profileSelect.addActionListener(l -> profileManager.setActiveProfile((TilemanProfile) profileSelect.getSelectedItem()));

            JButton deleteProfileButton = new JButton("X");

            //profileSelectionPanel.add(profileSelect);
            if (activeProfile.equals(TilemanProfile.NONE)) {
                //profileSelectionPanel.add(createProfileButton);
            } else {
                profileSelectionPanel.add(deleteProfileButton);
            }
        }

        return profileSelectionPanel;
    }

    private JPanel buildGameRulesPanel() {
        // Callback queue that gets run in reverse order. Mainly so we can properly manage enabling/disabling interactions
        List<Runnable> callbacks = new ArrayList<>();

        TilemanGameRules gameRules = profileManager.getGameRules();
        boolean hasActiveProfile = !profileManager.getActiveProfile().equals(TilemanProfile.NONE);

        JPanel gameRulesPanel = new JPanel();
        {
            gameRulesPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
            addVerticalLayout(gameRulesPanel);
            callbacks.add(() -> setJComponentEnabled(gameRulesPanel, hasActiveProfile));

            {
                JPanel gameModeSelectPanel = new JPanel();
                addVerticalLayout(gameModeSelectPanel);

                JPanel gameModeDropdownPanel = new JPanel();
                addFlowLayout(gameModeDropdownPanel);

                {
                    JLabel gameModeSelectLabel = new JLabel("Game Mode");

                    JComboBox gameModeSelect = new JComboBox(TilemanGameMode.values());
                    gameModeSelect.setSelectedItem(gameRules.getGameMode());
                    gameModeSelect.addActionListener(l -> profileManager.setGameMode((TilemanGameMode) gameModeSelect.getSelectedItem()));

                    gameModeDropdownPanel.add(gameModeSelectLabel);
                    gameModeDropdownPanel.add(gameModeSelect);
                }

                JCheckBox customGameMode = new JCheckBox("Enable Custom Game Mode");
                customGameMode.setAlignmentX(CENTER_ALIGNMENT);
                customGameMode.setSelected(gameRules.isEnableCustomGameMode());
                customGameMode.addActionListener(l ->  {
                    profileManager.setCustomGameMode(customGameMode.isSelected());
                    rebuild();
                });

                gameModeSelectPanel.add(gameModeDropdownPanel);
                gameModeSelectPanel.add(customGameMode);

                gameRulesPanel.add(gameModeSelectPanel);
            }

            {
                JPanel rulesPanel = new JPanel();
                addVerticalLayout(rulesPanel);
                callbacks.add(() -> setJComponentEnabled(rulesPanel, gameRules.isEnableCustomGameMode()));

                {
                    JCheckBox allowTileDeficit = new JCheckBox("Allow Tile Deficit");
                    allowTileDeficit.setAlignmentX(CENTER_ALIGNMENT);
                    allowTileDeficit.setSelected(gameRules.isAllowTileDeficit());
                    allowTileDeficit.addActionListener(l -> profileManager.setAllowTileDeficit(allowTileDeficit.isSelected()));
                    rulesPanel.add(allowTileDeficit);
                }

                {
                    JCheckBox includeTotalLevel = new JCheckBox("Tiles From Levels");
                    includeTotalLevel.setAlignmentX(CENTER_ALIGNMENT);
                    includeTotalLevel.setSelected(gameRules.isIncludeTotalLevel());
                    includeTotalLevel.addActionListener(l -> profileManager.setIncludeTotalLevel(includeTotalLevel.isSelected()));
                    rulesPanel.add(includeTotalLevel);
                }

                {
                    JCheckBox excludeExp = new JCheckBox("Exclude XP Tiles");
                    excludeExp.setAlignmentX(CENTER_ALIGNMENT);
                    excludeExp.setSelected(gameRules.isExcludeExp());
                    excludeExp.addActionListener(l -> profileManager.setExcludeExp(excludeExp.isSelected()));
                    rulesPanel.add(excludeExp);
                }



                {
                    JPanel tileOffsetPanel = new JPanel();
                    addFlowLayout(tileOffsetPanel);

                    JLabel tileOffsetLabel = new JLabel("Tile Offset");
                    tileOffsetPanel.add(tileOffsetLabel);

                    SpinnerNumberModel numberModel = new SpinnerNumberModel(gameRules.getTilesOffset(), MIN_TILE_OFFSET, MAX_TILE_OFFSET, 1);
                    JSpinner tilesOffsetSpinner = new JSpinner(numberModel);
                    tilesOffsetSpinner.addChangeListener(l -> profileManager.setTileOffset(numberModel.getNumber().intValue()));
                    tileOffsetPanel.add(tilesOffsetSpinner);
                    rulesPanel.add(tileOffsetPanel);
                }


                {
                    JPanel xpPanel = new JPanel();
                    addFlowLayout(xpPanel);

                    JLabel expPerTileLabel = new JLabel("Exp Per Tile");

                    SpinnerNumberModel numberModel = new SpinnerNumberModel(gameRules.getExpPerTile(), MIN_EXP_PER_TILE, MAX_EXP_PER_TILE, 1);
                    JSpinner expPerTileSpinner = new JSpinner(numberModel);
                    expPerTileSpinner.addChangeListener(l -> profileManager.setExpPerTile(numberModel.getNumber().intValue()));

                    xpPanel.add(expPerTileLabel);
                    xpPanel.add(expPerTileSpinner);

                    rulesPanel.add(xpPanel);
                }

                gameRulesPanel.add(rulesPanel);
            }
        }

        callbacks.forEach(func -> func.run());
        return gameRulesPanel;
    }

    private static void addHorizontalLayout(JComponent element) {
        element.setLayout(new BoxLayout(element, BoxLayout.X_AXIS));
        element.setBorder(BorderFactory.createLineBorder(Color.black));
    }

    private static void addVerticalLayout(JComponent element) {
        element.setLayout(new BoxLayout(element, BoxLayout.PAGE_AXIS));
        element.setBorder(BorderFactory.createLineBorder(Color.black));
    }

    private static void addFlowLayout(JComponent element) {
        element.setLayout(new FlowLayout());
    }

    private static void addSpacer(JComponent element) {
        addSpacer(element, 10);
    }

    private static void addSpacer(JComponent element, int height) {
        element.add(Box.createVerticalStrut(height));
    }

    private void setJComponentEnabled(JComponent element, boolean state) {
        element.setEnabled(state);
        Component[] children = element.getComponents();
        for (Component child : children) {
            if (child instanceof JPanel) {
                setJComponentEnabled((JPanel)child, state);
            }
            child.setEnabled(state);
        }
    }
}