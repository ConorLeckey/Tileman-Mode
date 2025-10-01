package com.tileman;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
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
    private TilemanModePlugin plugin;
    private ConfigManager configManager;
    private Set<String> importedDataSetKeys = new HashSet<>();

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

        // add the root panel so it displays on the plugin panel
        add(panel);
    }

    private void populateListOfImportedTiles(){

        // process the config file to determine imported data sets
        String prefix = "tilemanMode.imported_";
        List<String> configString = configManager.getConfigurationKeys(prefix);
        Set<String> cleanKeys = new HashSet<>();
        for (String key : configString){

            // scrub the prefix from the front of the string
            key = key.substring(prefix.length());

            // get the label for the dataset
            int underscoreIndex = key.indexOf('_');
            String cleanLabel = key.substring(0, underscoreIndex);
            cleanKeys.add(cleanLabel);
        }

        // display imported data sets
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
        deleteButton.addActionListener(l -> deleteButtonClicked(key));
        panel.add(deleteButton, constraints);
        constraints.gridy++;
    }

    private void deleteButtonClicked(String dataSetName) {
        log.debug(dataSetName);
        List<String> allKeys = configManager.getConfigurationKeys("tilemanMode" + "." + "imported_" + dataSetName);
        for (String key : allKeys) {
            String prefixToScrub = "tilemanMode.";
            key = key.substring(prefixToScrub.length());
            configManager.unsetConfiguration("tilemanMode", key);
            log.debug("scrubbed config key: " + "tilemanMode." + key);
        }

        // write to disk after deleting all the keys
        configManager.sendConfig();

        // rebuild the visual menu
        updatePanelContents();

        // update the tiles that the player can visually see on screen around them since tiles have been deleted
        plugin.updateTilesToRender();
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
    }

    private void importButtonClicked() {
        log.debug("===== Tileman Data Import Summary =====");

        // Exit early if we can't get the clipboard text contents
        final String clipboardText;
        try {
            clipboardText = Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .getData(DataFlavor.stringFlavor)
                    .toString();
        } catch (IOException | UnsupportedFlavorException ex) {
            log.debug(" | There was an error while reading from the clipboard", ex);
            return;
        }

        // Exit early if we can't validate the data is a string
        if (Strings.isNullOrEmpty(clipboardText)) {
            log.debug(" | No text was found on the clipboard.");
            return;
        }

        processGroupTilemanImport(clipboardText);
    }

    private void processGroupTilemanImport(String clipboardText){

        // Config related string keys used in this function should not be updated.
        // They have been statically implemented to ensure backwards compatibility with data exported
        // from the legacy group tileman plugin https://github.com/Flexz9/Tileman-GroupMode

        // Read group tileman export string to v2 data format store
        GroupTilemanData parsedData;
        try {
            Gson gson = new Gson();
            parsedData = gson.fromJson(clipboardText, GroupTilemanData.class);
        } catch (JsonSyntaxException e) {
            log.debug(" | The text on the clipboard was unable to be parsed. Abandoning import.", e);
            return;
        }

        // clean the label by scrubbing all non-alphanumeric characters as these can interfere with parsing
        String cleaningRegex = "[^a-zA-Z0-9]";
        String cleanLabel = parsedData.playerName.replaceAll(cleaningRegex, "");

        // clean any existing data stored under the same key name
        List<String> allKeys = configManager.getConfigurationKeys("tilemanMode" + "." + "imported_" + cleanLabel);
        deleteButtonClicked(cleanLabel);

        // write the imported data to the config store
        for (String regionStr : parsedData.regionTiles.keySet()) {
            List<TilemanModeTile> regionTiles = parsedData.regionTiles.get(regionStr);
            String prefixToScrub = "region_";
            int regionId = Integer.parseInt(regionStr.substring(prefixToScrub.length()));
            for(int plane = 0; plane < 4; plane++){

                // split the region data to planes
                List<TilemanModeTile> filteredTiles = new ArrayList<TilemanModeTile>();
                for (TilemanModeTile tile : regionTiles) {
                    if (tile.getZ() == plane){
                        filteredTiles.add(tile);
                    }
                }

                String key = "imported_" + cleanLabel + "_" + regionId + "_" + plane;
                plugin.writeV2FormatData(filteredTiles, key);
            }
        }

        // save to disk since we've imported new data
        configManager.sendConfig();

        // rebuild the visual menu
        updatePanelContents();

        // update the tiles that the player can visually see on screen around them based on the new import data
        plugin.updateTilesToRender();

        log.debug(" | Tiles successfully imported under label " + cleanLabel);
    }

    private void exportButtonClicked() {

        // Config related string keys used in this function should not be updated.
        // They have been statically implemented to generate an equivalent export string as
        // from the legacy group tileman plugin https://github.com/Flexz9/Tileman-GroupMode

        // prepare the export structure for the data export
        GroupTilemanData exportData = new GroupTilemanData();
        exportData.playerName = plugin.getPlayerName();
        exportData.regionTiles = new TreeMap<>();

        // collect config keys that need processing into the export data structure
        Set<Integer> regionsToExport = plugin.getAllRegionIds("tilemanMode", "regionv2_");

        // iterate all regions and collect the tiles into an export string
        for (int regionId : regionsToExport) {
            List<TilemanModeTile> tiles = new ArrayList<>();
            for (int plane = 0; plane < 4; plane++) {
                tiles.addAll(plugin.readTiles(regionId, plane));
            }
            exportData.regionTiles.put("region_" + String.valueOf(regionId), tiles);
        }

        Gson gson = new Gson();
        final String exportDump = gson.toJson(exportData);
        log.debug("Exported ground markers: {}", exportDump);
        Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(exportDump), null);
    }

}