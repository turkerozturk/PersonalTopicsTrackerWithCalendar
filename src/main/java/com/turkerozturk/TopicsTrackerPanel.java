/*
 * This file is part of the PersonalTopicsTrackerWithCalendar project.
 * Please refer to the project's README.md file for additional details.
 * https://github.com/turkerozturk/PersonalTopicsTrackerWithCalendar
 *
 * Copyright (c) 2025 Turker Ozturk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.en.html>.
 */
package com.turkerozturk;

import com.turkerozturk.initial.ExtensionCategory;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public class TopicsTrackerPanel extends JPanel implements PanelPlugin {

    ResourceBundle bundle = ResourceBundle.getBundle("application"); // application.properties

    private JTable table;
    private JLabel monthLabel;

    private JLabel cellContentLabel;

    JTextField searchField;
    private LocalDate currentDate;
    private Map<String, Integer> topicTotals;
    private DefaultTableModel tableModel;
    private List<String> topics;

    TableRowSorter<DefaultTableModel> sorter;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private Connection connection;

    private Database database;

    public TopicsTrackerPanel() {

        try {
            connection = DriverManager.getConnection("jdbc:sqlite:calendar_tracker.db");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }


        database = new Database(connection);
        database.setupDatabase();

        topics = database.loadTopics();



        currentDate = LocalDate.now();
        topicTotals = new HashMap<>();

        for (String topic : topics) {
            topicTotals.put(topic, 0);
        }


        // Ana panel oluştur ve BoxLayout kullan
       // JPanel mainPanel = new JPanel();
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); // Yatay eksende sıralama

        JPanel navigationPanel = new JPanel(new BorderLayout());
        //navigationPanel.setBackground(Color.RED);

        cellContentLabel = new JLabel(bundle.getString("message.hover.over.a.cell"), SwingConstants.CENTER);

        JPanel cellContentPanel = new JPanel(new BorderLayout());
        cellContentPanel.add(cellContentLabel, BorderLayout.CENTER);
        //cellContentPanel.setBackground(Color.BLUE);

        JButton prevMonthButton = new JButton(bundle.getString("button.previous.month"));
        JButton nextMonthButton = new JButton(bundle.getString("button.next.month"));
        JButton manageTopicsButton = new JButton(bundle.getString("button.topic.management"));
        monthLabel = new JLabel("", SwingConstants.CENTER);
        String formattedDate = updateMonthLabel();
        monthLabel.setText(formattedDate);

        prevMonthButton.addActionListener(e -> changeMonth(-1));
        nextMonthButton.addActionListener(e -> changeMonth(1));
        manageTopicsButton.addActionListener(e -> manageTopics());

        navigationPanel.add(prevMonthButton, BorderLayout.WEST);
        navigationPanel.add(monthLabel, BorderLayout.CENTER);

        navigationPanel.add(nextMonthButton, BorderLayout.EAST);
        navigationPanel.add(manageTopicsButton, BorderLayout.NORTH);

        JPanel searchPanel = new JPanel(new BorderLayout());
        //searchPanel.setBackground(Color.GREEN);
        JLabel searchLabel = new JLabel("Filter Topic Text: ", SwingConstants.LEFT);
        searchPanel.add(searchLabel, BorderLayout.WEST);

        tableModel = new DefaultTableModel();
        table = new JTable(tableModel) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column > 0 && column < getColumnCount() - 1;
            }

            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (column > 0 && column < getColumnCount() - 1) {
                    String topic = (String) getValueAt(row, 0);
                    YearMonth yearMonth = YearMonth.of(currentDate.getYear(), currentDate.getMonth());
                    if (column <= yearMonth.lengthOfMonth()) {
                        String date = currentDate.withDayOfMonth(column).toString();
                        int status = database.getCellStatus(topic, date);
                        if (status == 1) {
                            c.setBackground(Color.GREEN);
                        } else if (status == 2) {
                            c.setBackground(Color.RED);
                        } else {
                            c.setBackground(Color.WHITE);
                        }
                    } else {
                        c.setBackground(Color.WHITE);
                    }
                }
                return c;
            }
        };

        // Sıralayıcı ekleniyor
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        // topPanel2.add(new JLabel("Search: "), BorderLayout.WEST);

        // Arama alanı
        searchField = new JTextField(20);
        searchPanel.add(searchField, BorderLayout.CENTER);

        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filterTable();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filterTable();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filterTable();
            }


        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) ) {
                    if (e.getClickCount() == 1) {
                        int row = table.getSelectedRow();
                        int column = table.getSelectedColumn();
                        YearMonth yearMonth = YearMonth.of(currentDate.getYear(), currentDate.getMonth());
                        int daysInMonth = yearMonth.lengthOfMonth();

                        if (column > 0 && column <= daysInMonth) {
                            String topic = (String) table.getValueAt(row, 0);
                            String date = currentDate.withDayOfMonth(column).toString();
                            database.toggleCellStatus(topic, date);
                            updateTotals();
                            String whenLast = database.updateWhenLast(topic, currentDate);
                            tableModel.setValueAt(whenLast, row, tableModel.getColumnCount() - 1);

                            table.repaint();
                        }
                    }
                }
            }
        });




        table.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int column = table.columnAtPoint(e.getPoint());

                if (row >= 0 && column >= 0) {
                    Object value = table.getValueAt(row, column);
                    table.setToolTipText(value != null ? value.toString() : "");
                    if (value != null) {
                        cellContentLabel.setText(" "+ value.toString()); //Hovered text: //this space char is to hold the placeholder height of the label
                    } else {
                        cellContentLabel.setText(" ");//Hovered text: //this space char is to clear the label for mepty cells and hold the placeholder height of the label
                    }
                }
            }
        });


        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        //frame.setLayout(new BorderLayout());

        this.add(navigationPanel);
        this.add(Box.createRigidArea(new Dimension(0, 10))); // Araya boşluk eklemek için
        this.add(searchPanel);
        this.add(Box.createRigidArea(new Dimension(0, 10))); // Araya boşluk eklemek için
        this.add(cellContentPanel);
        this.add(Box.createRigidArea(new Dimension(0, 10))); // Araya boşluk eklemek için
        this.add(scrollPane);
        this.add(Box.createRigidArea(new Dimension(0, 10))); // Araya boşluk eklemek için

        updateTable(database.getConnection());


    }

    private void filterTable() {
        String searchText = searchField.getText();
        if (searchText.trim().isEmpty()) {
            sorter.setRowFilter(null); // Filtreyi kaldır
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchText, 0)); // İlk sütunda arama
        }
    }





    private String updateMonthLabel() {
        String localeCode = bundle.getString("app.locale");
        Locale locale = new Locale(localeCode);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale);
        String formattedDate = currentDate.format(formatter);
        return formattedDate;
    }

    private void changeMonth(int delta) {
        currentDate = currentDate.plusMonths(delta);
        String formattedDate = updateMonthLabel();
        monthLabel.setText(formattedDate);
        updateTable(database.getConnection());
        filterTable();
    }


    private void updateTable(Connection connection) {
        YearMonth yearMonth = YearMonth.of(currentDate.getYear(), currentDate.getMonth());
        int daysInMonth = yearMonth.lengthOfMonth();

        String[] columnNames = new String[daysInMonth + 3]; // Ekstra sütun için alan ayır
        columnNames[0] = bundle.getString("cell.topic.header");
        for (int i = 1; i <= daysInMonth; i++) {
            columnNames[i] = String.valueOf(i);
        }
        columnNames[daysInMonth + 1] = bundle.getString("cell.monthly.sum.header");
        columnNames[daysInMonth + 2] = bundle.getString("cell.when.last.header");

        tableModel.setDataVector(new Object[topics.size()][columnNames.length], columnNames);



        for (int swingTableRowNumber = 0; swingTableRowNumber < topics.size(); swingTableRowNumber++) {
            String topic = topics.get(swingTableRowNumber);
            tableModel.setValueAt(topic, swingTableRowNumber, 0);

            for (int day = 1; day <= daysInMonth; day++) {
                String date = currentDate.withDayOfMonth(day).toString();

                String content = database.getContent(topic, date);
                tableModel.setValueAt(content, swingTableRowNumber, day); // Hücreye content yükle

            }


            // when last
            String whenLast = database.updateWhenLast(topic, currentDate); //turker
            tableModel.setValueAt(whenLast, swingTableRowNumber, tableModel.getColumnCount() - 1);


        }

        tableModel.addTableModelListener(e -> {
            int row = e.getFirstRow();
            int column = e.getColumn();
            if (column > 0 && column < tableModel.getColumnCount() - 2) { // Monthly Sum ve When Last'i dışarıda tut
                String topic = (String) tableModel.getValueAt(row, 0); // İlk sütun topic ismini içeriyor
                long topicId = database.getTopicIdByName(topic); // Topic ismine göre ID'yi al
                if (topicId == -1) {
                    System.err.println("Topic ID bulunamadı: " + topic);
                    return; // ID bulunamazsa işlemi durdur
                }
                String date = currentDate.withDayOfMonth(column).toString();
                String content = (String) tableModel.getValueAt(row, column);
                database.updateCellContent(topicId, date, content); // topicId ile hücre içeriğini güncelle
            }
        });

        SwingUtilities.invokeLater(() -> {
            table.getColumnModel().getColumn(0).setMinWidth(100);
            table.getColumnModel().getColumn(0).setMaxWidth(100);

            DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
            centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
            for (int i = 1; i <= daysInMonth; i++) {
                table.getColumnModel().getColumn(i).setMinWidth(20);
                table.getColumnModel().getColumn(i).setMaxWidth(20);

                table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
            }
            table.getColumnModel().getColumn(daysInMonth + 1).setMinWidth(75);
            table.getColumnModel().getColumn(daysInMonth + 1).setMaxWidth(75);

            table.getColumnModel().getColumn(daysInMonth + 1).setCellRenderer(centerRenderer);
            table.getColumnModel().getColumn(daysInMonth + 2).setMinWidth(150); // When Last sütunu genişliği
            table.getColumnModel().getColumn(daysInMonth + 2).setMaxWidth(150); // When Last sütunu genişliği

            //table.getColumnModel().getColumn(daysInMonth + 2).setCellRenderer(centerRenderer);
        });

        updateTotals();
    }





    private void updateTotals() {
        for (int i = 0; i < topics.size(); i++) {
            String topic = topics.get(i);
            int total = 0;
            YearMonth yearMonth = YearMonth.of(currentDate.getYear(), currentDate.getMonth());
            for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
                String date = currentDate.withDayOfMonth(day).toString();
                if (database.getCellStatus(topic, date) == 1) {
                    total++;
                }
            }
            tableModel.setValueAt(String.format("%02d", total), i, tableModel.getColumnCount() - 2);
        }
    }

    private void updateWhenLasts() {

    }


    private void manageTopics() {
        JFrame manageFrame = new JFrame(bundle.getString("window.title.topic.management"));
        manageFrame.setSize(400, 200);
        manageFrame.setLayout(new BorderLayout());

        DefaultListModel<String> listModel = new DefaultListModel<>();
        topics.forEach(listModel::addElement);
        JList<String> topicList = new JList<>(listModel);

        JButton addButton = new JButton(bundle.getString("button.add.topic"));
        JButton removeButton = new JButton(bundle.getString("button.delete.topic"));
        JButton renameButton = new JButton(bundle.getString("button.rename.topic"));

        addButton.addActionListener(e -> {
            Object[] options = {bundle.getString("dialog.button.ok"), bundle.getString("dialog.button.cancel")};

            String newTopic = JOptionPane.showInputDialog(manageFrame, bundle.getString("dialog.label.add.topic.name"));
            if(database.addTopic(newTopic, topics)) {
                listModel.addElement(newTopic);
                updateTable(database.getConnection());
            }

        });

        removeButton.addActionListener(e -> {
            String selectedTopic = topicList.getSelectedValue();
            if (selectedTopic != null) {
                int confirm = JOptionPane.showConfirmDialog(manageFrame, MessageFormat.format(bundle.getString("confirm.are.you.sure.to.delete.topic"), selectedTopic), bundle.getString("dialog.delete.confirmation.title"), JOptionPane.YES_NO_OPTION);



                if (confirm == JOptionPane.YES_OPTION) {

                    if(database.deleteTopic(selectedTopic)) {
                        // Listeyi ve tabloyu güncelle
                        topics.remove(selectedTopic);
                        listModel.removeElement(selectedTopic);
                        updateTable(database.getConnection());
                    }


                }





            }
        });

        renameButton.addActionListener(e -> {
            String selectedTopic = topicList.getSelectedValue();
            if (selectedTopic != null) {
                String newTopicName = JOptionPane.showInputDialog(manageFrame, bundle.getString("dialog.label.rename.topic.name"), selectedTopic);
                if (newTopicName != null && !newTopicName.trim().isEmpty() && !topics.contains(newTopicName)) {
                    if(database.updateTopic(newTopicName, selectedTopic)) {
                        topics.set(topics.indexOf(selectedTopic), newTopicName);
                        listModel.set(listModel.indexOf(selectedTopic), newTopicName);
                        updateTable(database.getConnection());
                    }

                }
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(renameButton);

        manageFrame.add(new JScrollPane(topicList), BorderLayout.CENTER);
        manageFrame.add(buttonPanel, BorderLayout.SOUTH);

        manageFrame.setVisible(true);
    }


    @Override
    public String getTabName() {
        return "TopicsTracker";
    }

    @Override
    public JPanel getPanel() {
        return this;
    }

    @Override
    public ExtensionCategory getExtensionCategory() {
        return ExtensionCategory.PRODUCTIVITY;
    }

    @Override
    public String getExtensionDescription() {
        return "Personal Topics Tracker";
    }
}
