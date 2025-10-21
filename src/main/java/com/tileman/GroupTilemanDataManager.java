package com.tileman;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.FlatTextField;

import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.awt.datatransfer.DataFlavor;
import java.util.List;

import com.google.common.base.Strings;
import com.google.gson.Gson;

class GroupTilemanData {
    String playerName;
    TreeMap<String, List<TilemanModeTile>> regionTiles;
}

@Slf4j
@Singleton
public class GroupTilemanDataManager extends PluginPanel {

    private JPanel panel;
    private GridBagConstraints constraints;
    final private TilemanModePlugin plugin;
    final private ConfigManager configManager;
    final private Set<String> importedDataSetKeys = new HashSet<>();
    final private Color NEUTRAL_COLOR = new Color(0, 0, 0);
    final private Color FAILURE_RED = new Color(100, 0, 0);
    final private Color SUCCESS_GREEN = new Color(0, 100, 0);

    public GroupTilemanDataManager(TilemanModePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        updatePanelContents();
    }

    public Set<String> getImportedDataSetKeys(){
        return importedDataSetKeys;
    }

    private void updatePanelContents() {
        // clean any previous panel contents ready for a fresh update
        removeAll();

        // stylize the panel
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // create the panel and grid to display elements in
        panel = new JPanel(new GridBagLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.gridx = 0;
        constraints.gridy = 0;

        // add contents
        addTitleToLayout("Group Tileman Data");
        addDividerToLayout(10);
        addButtonsToLayout();
        addDividerToLayout(10);
        addTitleToLayout("Imported Tile Sets:");
        addDividerToLayout(10);
        populateListOfImportedTiles();

        // add the root panel, so it displays on the plugin panel
        add(panel);
    }

    private void populateListOfImportedTiles(){

        // process the config file to determine imported tile sets
        String prefix = TilemanModePlugin.CONFIG_GROUP + "." + TilemanModePlugin.REGION_PREFIX_IMPORTED;
        List<String> configString = configManager.getConfigurationKeys(prefix);
        Set<String> cleanKeys = new HashSet<>();
        for (String key : configString){

            // scrub the prefix from the front of the string
            key = key.substring(prefix.length());

            // advance forward in the string until _ is encountered, using the characters traveled over as the label
            int underscoreIndex = key.indexOf('_');
            String cleanLabel = key.substring(0, underscoreIndex);
            cleanKeys.add(cleanLabel);
        }

        // display imported tile sets
        for (String key : cleanKeys){
            {
                importedDataSetKeys.add(key);
                addDataEntry(key);
            }
            addDividerToLayout(5);
        }

    }

    private void addDataEntry(String key) {
        // create the name related fields
        FlatTextField keyField = new FlatTextField();
        keyField.setText(key);
        keyField.setEditable(false);
        keyField.setHoverBackgroundColor(Color.ORANGE);
        panel.add(keyField, constraints);
        constraints.gridy++;

        // add a delete button with input handling
        JButton deleteButton = new JButton("Delete");
        deleteButton.addActionListener(l -> deleteTileSet(key, false));
        panel.add(deleteButton, constraints);
        constraints.gridy++;
    }

    private void deleteTileSet(String tileSetName, boolean silent) {

        // deleteTileSet is triggered silently when updating a tileset that has already been imported.
        // It first deletes it, then imports fresh from the import string.
        if (!silent) {
            String chatMessage = new ChatMessageBuilder()
                    .append(NEUTRAL_COLOR, "Deleting tile set " + tileSetName + "...")
                    .build();
            plugin.sendChatMessage(chatMessage);
        }

        // walk all imported keys matching that tile set name, then delete them.
        String configGroup = TilemanModePlugin.CONFIG_GROUP + ".";
        List<String> allKeys = configManager.getConfigurationKeys(configGroup + TilemanModePlugin.REGION_PREFIX_IMPORTED + tileSetName);
        for (String key : allKeys) {
            log.debug("Scrubbing config key: " + key);
            key = key.substring(configGroup.length());
            configManager.unsetConfiguration(TilemanModePlugin.CONFIG_GROUP, key);
        }

        // write to disk after deleting all the keys
        configManager.sendConfig();

        // rebuild the visual menu
        updatePanelContents();

        // update the tiles that the player can visually see on screen around them since tiles have been deleted
        plugin.updateTilesToRender();

        // provide feedback to the player
        if (!silent) {
            String chatMessage = new ChatMessageBuilder()
                    .append(SUCCESS_GREEN, "Tile set " + tileSetName + " was removed.")
                    .build();
            plugin.sendChatMessage(chatMessage);
        }
    }

    private void addTitleToLayout(String label) {
        JLabel title = new JLabel();
        title.setText(label);
        title.setForeground(Color.ORANGE);
        panel.add(title, constraints);
        constraints.gridy++;
    }

    private void addDividerToLayout(int height) {
        panel.add(Box.createRigidArea(new Dimension(0, height)), constraints);
        constraints.gridy++;
    }

    private void addButtonsToLayout() {

        // create the import button
        JButton importButton = new JButton("Import from clipboard (Paste)");
        panel.add(importButton, constraints);
        importButton.addActionListener(l -> importButtonClicked());
        importButton.setToolTipText("Import Tileman Data from the string currently copied to the system clipboard.");
        constraints.gridy++;

        // tiny divider
        addDividerToLayout(5);

        // create the export button
        JButton exportButton = new JButton("Export to clipboard (Copy)");
        panel.add(exportButton, constraints);
        exportButton.addActionListener(l -> exportButtonClicked());
        exportButton.setToolTipText("Export your claimed tiles as plaintext Tileman Data to the clipboard for sharing.");
        constraints.gridy++;

        // provide optional cleanup functionality for legacy group tileman data
        List<String> legacyKeys = configManager.getConfigurationKeys(TilemanModePlugin.LEGACY_GROUP_TILEMAN_CONFIG_GROUP);
        if (legacyKeys != null && !legacyKeys.isEmpty()){

            // tiny divider
            addDividerToLayout(5);

            JButton purgeButton = new JButton("Purge legacy Group tileman data");
            panel.add(purgeButton, constraints);
            purgeButton.addActionListener(l -> purgeButtonClicked());
            purgeButton.setToolTipText("Deletes data from the old 'Group tileman addon' plugin (deprecated). "
                    + "May improve performance if you had previously imported many group tiles.");
            constraints.gridy++;
        }
    }

    private void purgeButtonClicked() {

        // provide immediate feedback when the button is clicked
        String start = new ChatMessageBuilder()
                .append(NEUTRAL_COLOR, "Cleaning up 'Group tileman addon' config file data...")
                .build();
        plugin.sendChatMessage(start);

        // walk all legacy keys and delete them
        String groupName = TilemanModePlugin.LEGACY_GROUP_TILEMAN_CONFIG_GROUP;
        List<String> legacyKeys = configManager.getConfigurationKeys(groupName + ".");
        for (String key : legacyKeys) {
            String cleanKey = key.substring(groupName.length() + 1);
            configManager.unsetConfiguration(groupName, cleanKey);
        }

        // save to disk since we've removed a significant volume of config data
        configManager.sendConfig();

        // rebuild the visual menu since this purge button should now disappear
        updatePanelContents();

        // provide results feedback
        String end = new ChatMessageBuilder()
                .append(SUCCESS_GREEN, legacyKeys.size() + " legacy config entries were successfully removed.")
                .build();
        plugin.sendChatMessage(end);
    }

    private void importButtonClicked() {

        String start = new ChatMessageBuilder()
                .append(NEUTRAL_COLOR, "Beginning tile set import from system clipboard...")
                .build();
        plugin.sendChatMessage(start);

        // Exit early if we can't get the clipboard text contents
        final String clipboardText;
        try {
            clipboardText = Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .getData(DataFlavor.stringFlavor)
                    .toString();
        } catch (IOException | UnsupportedFlavorException ex) {
            // this is so we grab the exception details in debug
            log.debug(" | There was an error while reading from the clipboard", ex);
            String chatMessage = new ChatMessageBuilder()
                    .append(FAILURE_RED, "Import failed. Unable to read the data from the system clipboard.")
                    .build();
            plugin.sendChatMessage(chatMessage);
            return;
        }

        // Exit early if we can't validate the data is a string
        if (Strings.isNullOrEmpty(clipboardText)) {
            String chatMessage = new ChatMessageBuilder()
                    .append(FAILURE_RED, "Import failed. No text was found on the clipboard.")
                    .build();
            plugin.sendChatMessage(chatMessage);
            return;
        }

        processGroupTilemanImport(clipboardText);
    }

    private void processGroupTilemanImport(String clipboardText){

        // Config related string keys used in this function should not be updated.
        // They have been statically implemented to ensure backwards compatibility with data exported
        // from the legacy group tileman plugin https://github.com/Flexz9/Tileman-GroupMode

        // convert export string to groupTilemanData
        GroupTilemanData parsedData;
        try {
            Gson gson = new Gson();
            parsedData = gson.fromJson(clipboardText, GroupTilemanData.class);
        } catch (JsonSyntaxException e) {
            log.debug("The text on the clipboard was unable to be parsed. Abandoning import.", e);
            String chatMessage = new ChatMessageBuilder()
                    .append(FAILURE_RED, "Import failed. Clipboard text was not a well formed group tileman tile set.")
                    .build();
            plugin.sendChatMessage(chatMessage);
            return;
        }

        // generate a sanitized pure alphanumeric label for the tile set
        String tileSetName;
        try {
            // guard against empty field contents
            if (parsedData.playerName == null){
                throw new IllegalArgumentException();
            }

            // clean the label by scrubbing all non-alphanumeric characters as these can interfere with parsing
            String cleaningRegex = "[^a-zA-Z0-9]";
            tileSetName = parsedData.playerName.replaceAll(cleaningRegex, "");

            // guard against the sanitized string being clean, but empty
            if (tileSetName.trim().isEmpty()){
                throw new IllegalArgumentException();
            }

        } catch (Exception e){
            log.debug("Unable to extract a sensible tile set name from the supplied string.", e);
            String badName = new ChatMessageBuilder()
                    .append(FAILURE_RED, "Import failed. Unable to generate a sensible tile set name from the clipboard contents.")
                    .build();
            plugin.sendChatMessage(badName);
            return;
        }

        // clean any existing data stored under the same key name
        deleteTileSet(tileSetName, true);

        // write the imported data to the config store
        int tilesImported = 0;
        for (String regionStr : parsedData.regionTiles.keySet()) {
            List<TilemanModeTile> regionTiles = parsedData.regionTiles.get(regionStr);
            int regionId = Integer.parseInt(regionStr.substring(TilemanModePlugin.REGION_PREFIX_V1.length()));
            for(int plane = 0; plane < 4; plane++){

                // split the region data to planes
                List<TilemanModeTile> filteredTiles = new ArrayList<>();
                for (TilemanModeTile tile : regionTiles) {
                    if (tile.getZ() == plane){
                        filteredTiles.add(tile);
                    }
                }

                String key = TilemanModePlugin.REGION_PREFIX_IMPORTED + tileSetName + "_" + regionId + "_" + plane;
                plugin.writeV2FormatData(filteredTiles, key);
                tilesImported += filteredTiles.size();
            }
        }

        // save to disk since we've imported new data
        configManager.sendConfig();

        // rebuild the visual menu
        updatePanelContents();

        // update the tiles that the player can visually see on screen around them based on the new import data
        plugin.updateTilesToRender();

        // provide some feedback to the player
        String chatMessage = new ChatMessageBuilder()
                .append(SUCCESS_GREEN, "Successfully imported " + tilesImported + " tiles into tile set " + tileSetName + "!")
                .build();
        plugin.sendChatMessage(chatMessage);
    }

    private void exportButtonClicked() {

        // Config related string keys used in this function should not be updated.
        // They have been statically implemented to generate an equivalent export string as
        // from the legacy group tileman plugin https://github.com/Flexz9/Tileman-GroupMode

        String start = new ChatMessageBuilder()
                .append(NEUTRAL_COLOR, "Beginning tile set export to system clipboard...")
                .build();
        plugin.sendChatMessage(start);

        // prepare the export structure for the data export
        GroupTilemanData exportData = new GroupTilemanData();
        exportData.playerName = plugin.getPlayerName();
        exportData.regionTiles = new TreeMap<>();

        // collect config keys that need processing into the export data structure
        Set<Integer> regionsToExport = plugin.getAllRegionIds(TilemanModePlugin.CONFIG_GROUP, TilemanModePlugin.REGION_PREFIX_V2);

        // iterate all regions and collect the tiles into an export string
        int tilesExported = 0;
        for (int regionId : regionsToExport) {
            List<TilemanModeTile> tiles = new ArrayList<>();
            for (int plane = 0; plane < 4; plane++) {
                tiles.addAll(plugin.readTiles(regionId, plane));
            }
            // V1 is used for legacy format compatibility with historic exports from group tileman addon plugin.
            exportData.regionTiles.put(TilemanModePlugin.REGION_PREFIX_V1 + regionId, tiles);
            tilesExported += tiles.size();
        }

        Gson gson = new Gson();
        final String exportDump = gson.toJson(exportData);
        Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(exportDump), null);

        // log the complete output to the console for developers.
        log.debug("Exported tile set: {}", exportDump);

        // provide player feedback
        String end = new ChatMessageBuilder()
                .append(SUCCESS_GREEN, "Successfully exported tile set containing " + tilesExported + " tiles to system clipboard!")
                .build();
        plugin.sendChatMessage(end);

    }

    void generateLegacyGroupTilemanPluginWarning() {

        // generates a warning for up to three independent gameplay sessions about legacy plugin behavior.

        // search for group tileman related config keys
        List<String> legacyKeys = configManager.getConfigurationKeys(TilemanModePlugin.LEGACY_GROUP_TILEMAN_CONFIG_GROUP + ".");
        String warningKey = "timesWarnedAboutGroupTilemanPlugin";
        String keyContents = configManager.getConfiguration(TilemanModePlugin.CONFIG_GROUP, warningKey);
        int warningCount = 0;
        if (keyContents != null){
            warningCount = Integer.parseInt(keyContents);
        }

        // manage maximum number of warnings to provide about this
        final int maxWarningsToGive = 3;
        final int remainingWarnings = maxWarningsToGive - (warningCount + 1);
        final Map<Integer, String> countMessage = new HashMap<>();
        countMessage.put(2, "This warning will display two more times.");
        countMessage.put(1, "This warning will display one more time.");
        countMessage.put(0, "This warning will not display again.");

        // actually show the warning the first three times
        if (!legacyKeys.isEmpty() && warningCount < maxWarningsToGive) {

            String warning = "Warning: Data from the 'Group tileman addon' plugin was detected. That plugin has been deprecated! "
                    + "Group tiles are now managed in the 'Group Tileman Data' menu (located on the far right navigation bar). "
                    + "Please have your group export and import tiles through this new interface. "
                    + countMessage.get(remainingWarnings);

            plugin.sendChatMessage(new ChatMessageBuilder().append(Color.RED, warning).build());
            configManager.setConfiguration(TilemanModePlugin.CONFIG_GROUP, warningKey, warningCount + 1);
        }
    }

}