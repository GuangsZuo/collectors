package gov.pnnl.stucco.utilities;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;


/**
 * Utility application to help manage the Collector Metadata database.
 * This utility shows the database as a table. It supports basic sorting
 * and regex filtering to control the view. Selected rows can be deleted,
 * and changes committed to the database. Upon closing, the user will be
 * warned if there are unsaved changes.
 */
public class CollectorMetadataUtility extends JFrame {
    // For Serializable
    private static final long serialVersionUID = 1034362215423567098L;

    // Swing components
    private JTable metadataTable;
    private JTextField findField;
    private JButton refreshButton;
    private JButton deleteButton;
    private JButton commitButton;

    /** Main collector metadata class. */
    private static final CollectorMetadata metadata = CollectorMetadata.getInstance();
    
    /** Content model backing the table */
    private CollectorMetadataTableModel metadataTableModel;
    
    /** Delegate managing table sorting and filtering */
    private TableRowSorter<CollectorMetadataTableModel> sorter;
    
    /** Whether the user has modified the table since the last commit. */
    private boolean changed = false;

    
    
    public CollectorMetadataUtility() {
        setTitle("Collector Metadata Utility");
        layOutUi(); 
        updateControlsState();
        initializeTableModel();
        addInternalListeners();
        addSortingAndFiltering();
    }
    
    /** Performs basic layout of the GUI. Mostly generated by the GUI builder. */
    private void layOutUi() {
        JPanel contentPanel = new JPanel();
        getContentPane().add(contentPanel, BorderLayout.CENTER);
        contentPanel.setLayout(new BorderLayout(0, 0));
        
        JPanel searchPanel = new JPanel();
        contentPanel.add(searchPanel, BorderLayout.NORTH);
        GridBagLayout gbl_searchPanel = new GridBagLayout();
        searchPanel.setLayout(gbl_searchPanel);
        
        JLabel findLabel = new JLabel("Find Regex:");
        GridBagConstraints gbc_findLabel = new GridBagConstraints();
        gbc_findLabel.anchor = GridBagConstraints.EAST;
        gbc_findLabel.insets = new Insets(5, 5, 5, 5);
        gbc_findLabel.gridx = 0;
        gbc_findLabel.gridy = 0;
        searchPanel.add(findLabel, gbc_findLabel);
        
        findField = new JTextField();
        Dimension preferredSize = findField.getPreferredSize();
        preferredSize.width = 500;
        findField.setPreferredSize(preferredSize);
        GridBagConstraints gbc_findField = new GridBagConstraints();
        gbc_findField.weightx = 1.0;
        gbc_findField.anchor = GridBagConstraints.WEST;
        gbc_findField.insets = new Insets(5, 0, 5, 15);
        gbc_findField.gridx = 1;
        gbc_findField.gridy = 0;
        searchPanel.add(findField, gbc_findField);
        
        refreshButton = new JButton();
        refreshButton.setText("Refresh");
        GridBagConstraints gbc_refreshButton = new GridBagConstraints();
        gbc_refreshButton.gridx = 2;
        gbc_refreshButton.gridy = 0;
        gbc_refreshButton.anchor = GridBagConstraints.EAST;
        gbc_refreshButton.insets = new Insets(5, 0, 5, 5);
        
        // Commented out for now. 
        //
        // The intent was to be able to refresh while scheduler-controlled 
        // collectors are adding to the database. However, MapDB doesn't 
        // support this kind of multiprocess concurrent access, and we are 
        // seeing exceptions as a result. We may revisit this and switch to 
        // something (BerkeleyDB?) that supports multiprocess concurrent access.
//        searchPanel.add(refreshButton, gbc_refreshButton);
        
        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setPreferredSize(new Dimension(1200, 500));
        contentPanel.add(scrollPane, BorderLayout.CENTER);
        
        metadataTable = new JTable();
        scrollPane.setViewportView(metadataTable);
        
        JPanel buttonPanel = new JPanel();
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);
        GridBagLayout gbl_buttonPanel = new GridBagLayout();
        buttonPanel.setLayout(gbl_buttonPanel);
        
        deleteButton = new JButton("Delete");
        GridBagConstraints gbc_deleteButton = new GridBagConstraints();
        gbc_deleteButton.insets = new Insets(5, 0, 5, 5);
        gbc_deleteButton.anchor = GridBagConstraints.EAST;
        gbc_deleteButton.gridx = 0;
        gbc_deleteButton.gridy = 0;
        gbc_deleteButton.weightx = 1;
        buttonPanel.add(deleteButton, gbc_deleteButton);
        
        commitButton = new JButton("Commit");
        GridBagConstraints gbc_commitButton = new GridBagConstraints();
        gbc_commitButton.anchor = GridBagConstraints.EAST;
        gbc_commitButton.insets = new Insets(5, 0, 5, 5);
        gbc_commitButton.gridx = 1;
        gbc_commitButton.gridy = 0;
        buttonPanel.add(commitButton, gbc_commitButton);
        
        pack();
    }
    
    /** Sets the initial column widths of the table. */
    private JTable setTableColumnWidths(JTable table) {
        TableColumnModel columnModel = table.getColumnModel();
        columnModel.getColumn(CollectorMetadataTableModel.URL_COLUMN).setPreferredWidth(800);
        columnModel.getColumn(CollectorMetadataTableModel.TIMESTAMP_COLUMN).setPreferredWidth(210);
        columnModel.getColumn(CollectorMetadataTableModel.UUID_COLUMN).setPreferredWidth(70);
        columnModel.getColumn(CollectorMetadataTableModel.HASH_COLUMN).setPreferredWidth(70);
        columnModel.getColumn(CollectorMetadataTableModel.ETAG_COLUMN).setPreferredWidth(50);
        
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        return table;
    }
    
    /** Adds the listeners used to hook up various controls internally. */
    private void addInternalListeners() {
        // To protect user on close
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent arg0) {
                if (handleUncommittedChanges()) {
                    System.exit(0);
                }
            }
        });

        // To update search
        findField.addCaretListener(new CaretListener() {
            public void caretUpdate(CaretEvent arg0) {
                search();
            }
        });
        
        // To refresh view from database
        refreshButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                refresh();
            }
        });
        
        // To delete rows
        deleteButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                removeSelectedRows();
                updateControlsState();
            }
        });
        
        // To commit changes
        commitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                commitChanges();
                updateControlsState();
            }
        });
        
        // To enable/disable controls
        ListSelectionModel selectionModel = metadataTable.getSelectionModel();
        selectionModel.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent arg0) {
                updateControlsState();
            }
        });
    }
    
    /** 
     * Checks for uncommitted changes, and gives the user the option of [Yes] save
     * them, [No] discard them, or [Cancel] the operation they initiated.
     * 
     * @return true if user chose [Yes] or [No], false if they chose [Cancel]
     */
    private boolean handleUncommittedChanges() {
        boolean ok = true;
        if (changed) {
            // Warn user about unsaved changes
            String title = "Save";
            String msg = "You have uncommitted changes. Save changes?";
            int choice = JOptionPane.showConfirmDialog(CollectorMetadataUtility.this, msg, title, JOptionPane.YES_NO_CANCEL_OPTION);
            
            switch (choice) {
                case JOptionPane.YES_OPTION:
                    // Save the changes
                    commitChanges();
                    break;
                    
                case JOptionPane.NO_OPTION:
                    // Discard the changes
                    rollbackChanges();
                    break;
                    
                case JOptionPane.CANCEL_OPTION:
                    // Cancel the next steps altogether
                    ok = false;
                    break;
            }
            
            updateControlsState();
        }
        
        return ok;
    }

    /** Adds support for sorting and filtering. */
    private void addSortingAndFiltering() {
        sorter = new TableRowSorter<CollectorMetadataTableModel>(metadataTableModel);
        metadataTable.setRowSorter(sorter);
    }
    
    /**
     * Backs the table with the database, reflecting any external additions to 
     * the database (as from other processes inserting data). 
     */
    private void initializeTableModel() {
        metadata.getLatestFromDb();
        
        metadataTableModel = new CollectorMetadataTableModel(metadata);
        metadataTable.setModel(metadataTableModel);
        
        setTableColumnWidths(metadataTable);
    }
    
    /** Updates the table filtering using the regex field. */
    private void search() {
        RowFilter<CollectorMetadataTableModel, Integer> filter;
        try {
            // Get the search text
            String text = findField.getText();
        
            // Set it as a filter
            filter = RowFilter.regexFilter(text);
        }
        catch (PatternSyntaxException pse) {
            // Bad regex
            filter = null;
        }
        
        sorter.setRowFilter(filter);
    }
    
    /** Removes the selected rows from the table. */
    private void removeSelectedRows() {
        // Get the selected table rows as view indexes
        int[] selectedViewRows = metadataTable.getSelectedRows();
        int selectionCount = selectedViewRows.length;
        
        // Convert view indexes to model indexes
        List<Integer> selectedModelRows = new ArrayList<Integer>(selectionCount);
        for (int i = 0; i < selectionCount; i++) {
            int selectedModelRow = metadataTable.convertRowIndexToModel(selectedViewRows[i]);
            selectedModelRows.add(selectedModelRow); 
        }
        
        // Remove them from the model
        metadataTableModel.removeAll(selectedModelRows);
        changed = true;
    }
    
    /** Reloads the database into this. */
    private void refresh() {
        if (handleUncommittedChanges()) {
            // Reset the table model
            initializeTableModel();
            
            addSortingAndFiltering();
        }
    }
    
    /** Commits the user's changes to the database. */
    private void commitChanges() {
        try {
            metadata.save();
            changed = false;
        } 
        catch (IOException e) {
            // Commit failed. There's nothing we can do except warn the user.
            String title = "IOException";
            String message = e.getMessage();
            JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    /** Rolls back any changes since the last commit. */
    private void rollbackChanges() {
        metadata.rollback();
        changed = false;
    }
    
    /** Enables or disables controls as appropriate. */
    private void updateControlsState() {
        boolean anySelected = (metadataTable.getSelectedRow() != -1);
        deleteButton.setEnabled(anySelected);
        commitButton.setEnabled(changed);
    }
    
    /** Runs the utility. */
    static public void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                CollectorMetadataUtility ui = new CollectorMetadataUtility();
                ui.setLocationRelativeTo(null);
                ui.setVisible(true);
            }
        });
    }
}
