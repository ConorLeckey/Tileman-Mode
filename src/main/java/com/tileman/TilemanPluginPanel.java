package com.tileman;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.util.ArrayList;
import java.util.List;

import java.awt.datatransfer.StringSelection;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class TilemanPluginPanel extends PluginPanel {

    private static final int MIN_EXP_PER_TILE = 250;
    private static final int MAX_EXP_PER_TILE = 100000;

    private static final int MIN_TILE_OFFSET = Integer.MIN_VALUE;
    private static final int MAX_TILE_OFFSET = Integer.MAX_VALUE;

    private final TilemanModePlugin plugin;
    private final TilemanProfileManager profileManager;
    private final Client client;

    private boolean showExportInfo = false;
    private boolean gameModeOpen = false;
    private boolean advancedOpen = false;

    public TilemanPluginPanel(TilemanModePlugin plugin, Client client, TilemanProfileManager profileManager) {
        this.plugin = plugin;
        this.client = client;
        this.profileManager = profileManager;
        build();
    }

    @Override
    public void onActivate() {
        rebuild();
    }

    public void rebuild() {
        SwingUtilities.invokeLater(() -> {
            build();
            revalidate();
            repaint();
        });
    }

    private void build() {
        this.removeAll();
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        {
            JPanel titlePanel = new JPanel(new BorderLayout());
            titlePanel.setBorder(BorderFactory.createLineBorder(Color.black));
            titlePanel.setBorder(new EmptyBorder(1, 0, 10, 0));

            JLabel title = new JLabel();
            title.setText("Tileman Mode");
            title.setForeground(Color.WHITE);
            titlePanel.add(title, BorderLayout.NORTH);

            this.add(titlePanel, BorderLayout.NORTH);
        }

        {
            JPanel bodyPanel = new JPanel();
            addVerticalLayout(bodyPanel);

            bodyPanel.add(buildProfilePanel());
            bodyPanel.add(buildGameRulesPanel());
            bodyPanel.add(buildAdvancedOptionsPanel());

            this.add(bodyPanel, BorderLayout.CENTER);
        }
    }

    private JPanel buildProfilePanel() {
        TilemanProfile activeProfile = profileManager.getActiveProfile();
        boolean isLoggedIn = plugin.isLoggedIn();

        JPanel profilePanel = new JPanel();
        profilePanel.setBorder(BorderFactory.createLineBorder(Color.black));
        addVerticalLayout(profilePanel);
        {
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
                    TilemanProfile profile = TilemanProfile.NONE;

                    Object[] options = new Object[] {"New Profile", "Import Existing Data"};
                    int choice = JOptionPane.showOptionDialog(null, "Create a profile:", "Create Profile", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, null);

                    if (choice == 0) {
                        profile = profileManager.createProfile();
                    } else if (choice == 1) {
                        options = new Object[] {"Import Old Tile Data", "Import Ground Marker Data", "Manual Import"};
                        choice = JOptionPane.showOptionDialog(null, "Choose how to import existing tile data:", "Import Existing Data", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, null);
                        if (choice == 0) {
                            profile = profileManager.createProfileWithLegacyData();
                        } else if (choice == 1) {
                            profile = profileManager.createProfileWithGroundMarkerData();
                        } else if (choice == 2) {
                            showProfileImportPanel();
                            return;
                        }
                    }
                    profileManager.setActiveProfile(profile);
                });
                profilePanel.add(createProfileButton);
            }
            addSpacer(profilePanel);
        }
        return profilePanel;
    }

    private JPanel buildGameRulesPanel() {
        // Callback queue, so we can properly manage enabling/disabling interactions without worrying about component build order.
        List<Runnable> callbacks = new ArrayList<>();

        TilemanGameRules gameRules = profileManager.getGameRules();
        boolean hasActiveProfile = !profileManager.getActiveProfile().equals(TilemanProfile.NONE);

        JPanel gameRulesPanel = new JPanel();
        gameRulesPanel.setBorder(BorderFactory.createLineBorder(Color.black));
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

                gameModeSelectPanel.add(gameModeDropdownPanel);
                gameRulesPanel.add(gameModeSelectPanel);
            }


            {
                JCollapsePanel customGameModeCollapsable = new JCollapsePanel("Custom Game Mode", gameModeOpen, (Boolean state) -> this.gameModeOpen = state);
                customGameModeCollapsable.setBorder(BorderFactory.createLineBorder(Color.black));
                customGameModeCollapsable.setInnerLayout(new BorderLayout());

                gameRulesPanel.add(customGameModeCollapsable);

                {
                    JCheckBox customGameMode = new JCheckBox("Enable Custom Game Mode");
                    customGameModeCollapsable.add(customGameMode, BorderLayout.NORTH);

                    customGameMode.setAlignmentX(CENTER_ALIGNMENT);
                    customGameMode.setSelected(gameRules.isEnableCustomGameMode());
                    customGameMode.addActionListener(l ->  {
                        profileManager.setCustomGameMode(customGameMode.isSelected());
                        rebuild();
                    });
                }

                {
                    JPanel rulesPanel = new JPanel();
                    addVerticalLayout(rulesPanel);
                    callbacks.add(() -> setJComponentEnabled(rulesPanel, gameRules.isEnableCustomGameMode()));

                    customGameModeCollapsable.add(rulesPanel, BorderLayout.CENTER);

                    {
                        JCheckBox allowTileDeficit = new JCheckBox("Allow Tile Deficit");
                        allowTileDeficit.setAlignmentX(CENTER_ALIGNMENT);
                        allowTileDeficit.setSelected(gameRules.isAllowTileDeficit());
                        allowTileDeficit.addActionListener(l -> profileManager.setAllowTileDeficit(allowTileDeficit.isSelected()));
                        rulesPanel.add(allowTileDeficit);
                    }

                    {
                        JCheckBox tilesFromLevels = new JCheckBox("Tiles From Levels");
                        tilesFromLevels.setAlignmentX(CENTER_ALIGNMENT);
                        tilesFromLevels.setSelected(gameRules.isTilesFromTotalLevel());
                        tilesFromLevels.addActionListener(l -> profileManager.setTilesFromTotalLevel(tilesFromLevels.isSelected()));
                        rulesPanel.add(tilesFromLevels);
                    }

                    {
                        JCheckBox tilesFromExp = new JCheckBox("Tiles From Exp");
                        tilesFromExp.setAlignmentX(CENTER_ALIGNMENT);
                        tilesFromExp.setSelected(gameRules.isTilesFromExp());
                        tilesFromExp.addActionListener(l -> profileManager.setTilesFromExp(tilesFromExp.isSelected()));
                        rulesPanel.add(tilesFromExp);
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
                }
            }

        }

        callbacks.forEach(func -> func.run());
        return gameRulesPanel;
    }

    private JPanel buildAdvancedOptionsPanel() {
        if (!plugin.isShowAdvancedOptions()) {
            return new JPanel();
        }

        JCollapsePanel advancedOptions = new JCollapsePanel("Advanced Options", advancedOpen, (Boolean isOpen) -> this.advancedOpen = isOpen);
        advancedOptions.setBorder(BorderFactory.createLineBorder(Color.black));
        addVerticalLayout(advancedOptions.getContentPanel());
        {
            JButton exportProfileButton = new JButton("Export Profile");
            exportProfileButton.setAlignmentX(CENTER_ALIGNMENT);
            exportProfileButton.setEnabled(profileManager.hasActiveProfile());
            advancedOptions.add(exportProfileButton);

            JLabel exportInfo = new JLabel("Copied to clipboard!");
            exportInfo.setAlignmentX(CENTER_ALIGNMENT);
            exportInfo.setVisible(showExportInfo);
            showExportInfo = false;
            advancedOptions.add(exportInfo);

            exportProfileButton.addActionListener(l -> {
                if (profileManager.hasActiveProfile()) {
                    copyToClipboard(profileManager.exportProfile());
                    showExportInfo = true;
                    rebuild();
                }
            });

            JButton deleteProfileButton = new JButton("Delete Profile");
            deleteProfileButton.setAlignmentX(CENTER_ALIGNMENT);
            deleteProfileButton.setEnabled(profileManager.hasActiveProfile());
            advancedOptions.add(deleteProfileButton);

            deleteProfileButton.addActionListener(l -> {
                if (profileManager.hasActiveProfile()) {
                    int choice = JOptionPane.showConfirmDialog(null, "Are you sure you want to delete this profile?\nAll Tile data will be lost.", "Delete Profile?", JOptionPane.YES_NO_OPTION);
                    if (choice == 0) {
                        choice = JOptionPane.showConfirmDialog(null, "This action cannot be undone!\nPress 'Yes' to delete the profile and associated data.", "Are you sure?", JOptionPane.YES_NO_OPTION);
                        if (choice == 0) {
                            profileManager.deleteActiveProfile();
                            rebuild();
                        }
                    }
                }
            });
        }
        return advancedOptions;
    }

    private void showProfileImportPanel() {
        if (!profileManager.hasActiveProfile()) {
            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());

            JLabel instructions = new JLabel("Paste (Ctrl-V) your exported profile data here:");
            panel.add(instructions, BorderLayout.NORTH);

            JTextArea importText = new JTextArea("");
            importText.setLineWrap(true);
            importText.setColumns(5);

            JScrollPane scrollPane = new JScrollPane(importText);
            scrollPane.setPreferredSize(new Dimension(225, 120));
            scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
            scrollPane.setAlignmentX(CENTER_ALIGNMENT);

            panel.add(scrollPane, BorderLayout.CENTER);

            int choice = JOptionPane.showConfirmDialog(null,
                    panel,
                    "Import Profile",
                    JOptionPane.YES_NO_OPTION);

            if (choice == 0) {
                String maybeJson = importText.getText();
                TilemanProfile profile = profileManager.importProfileAsNew(maybeJson, client.getAccountHash());
                profileManager.setActiveProfile(profile);
            }
        }
    }

    private static void addVerticalLayout(JComponent element) {
        element.setLayout(new BoxLayout(element, BoxLayout.PAGE_AXIS));
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

    private static void copyToClipboard(String text) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(text), null);
    }

    public class JCollapsePanel extends JPanel {

        private String title;
        private boolean isOpen;

        private JPanel contentPanel;
        private JButton toggleCollapseButton;

        private Consumer<Boolean> onToggle;

        public JCollapsePanel(String title, boolean isOpen, Consumer<Boolean> onToggle) {
            super();
            super.setLayout(new BorderLayout());

            this.title = title;
            this.onToggle = onToggle;
            this.contentPanel = new JPanel();
            super.add(contentPanel, BorderLayout.CENTER);

            toggleCollapseButton = new JButton();
            toggleCollapseButton.setFocusPainted(false);
            toggleCollapseButton.setHorizontalAlignment(SwingConstants.LEFT);
            toggleCollapseButton.addActionListener(l -> {
                setOpen(!this.isOpen);
                if (onToggle != null){
                    onToggle.accept(this.isOpen);
                }
            });
            super.add(toggleCollapseButton, BorderLayout.NORTH);

            setOpen(isOpen);
        }

        @Override
        public Component add(Component component) {
            return contentPanel.add(component);
        }

        @Override
        public void add(Component component, Object constraints) {
            contentPanel.add(component, constraints);
        }

        public JPanel getContentPanel() {
            return contentPanel;
        }

        public void setInnerLayout(LayoutManager layoutManager) {
            contentPanel.setLayout(layoutManager);
        }

        public void setOpen(boolean isOpen) {
            this.isOpen = isOpen;
            contentPanel.setVisible(isOpen);
            toggleCollapseButton.setText((isOpen ?"▼" :  "▶") + "    " + title);
        }
    }
}