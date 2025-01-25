package com.turkerozturk;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;

public class App {

    ResourceBundle bundle = ResourceBundle.getBundle("application"); // application.properties

    private JFrame frame;
    private JTable table;
    private JLabel monthLabel;

    private JLabel cellContentLabel;

    JTextField searchField;
    private LocalDate currentDate;
    private Map<String, Integer> topicTotals;
    private DefaultTableModel tableModel;
    private List<String> topics;

    TableRowSorter<DefaultTableModel> sorter;

    private Connection connection;

    public App() {
        setupDatabase();

        topics = loadTopics();

        frame = new JFrame(bundle.getString("window.title"));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 600);

        currentDate = LocalDate.now();
        topicTotals = new HashMap<>();

        for (String topic : topics) {
            topicTotals.put(topic, 0);
        }


        // Ana panel oluştur ve BoxLayout kullan
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS)); // Yatay eksende sıralama

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
                        int status = getCellStatus(topic, date);
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
                            toggleCellStatus(topic, date);
                            updateTotals();
                            String whenLast = updateWhenLast(topic);
                            tableModel.setValueAt(whenLast, row, tableModel.getColumnCount() - 1);

                            table.repaint();
                        }
                    }
                }
            }
        });


        //cellContentLabel.setBounds(10, 300, 300, 30);
        //cellContentLabel.setHorizontalAlignment(SwingConstants.LEFT);

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

        mainPanel.add(navigationPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10))); // Araya boşluk eklemek için
        mainPanel.add(searchPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10))); // Araya boşluk eklemek için
        mainPanel.add(cellContentPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10))); // Araya boşluk eklemek için
        mainPanel.add(scrollPane);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10))); // Araya boşluk eklemek için


        frame.add(mainPanel);

        //frame.add(topPanel2, BorderLayout.NORTH);

        //frame.add(scrollPane);

        updateTable();

        frame.setVisible(true);
    }

    private void filterTable() {
        String searchText = searchField.getText();
        if (searchText.trim().isEmpty()) {
            sorter.setRowFilter(null); // Filtreyi kaldır
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchText, 0)); // İlk sütunda arama
        }
    }

    private void setupDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:calendar_tracker.db");
            Statement statement = connection.createStatement();

            statement.execute("    CREATE TABLE IF NOT EXISTS activity_log (\n" +
                              "        id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                              "        topic_id INTEGER,\n" +
                              "        date TEXT,\n" +
                              "        is_marked INTEGER,\n" +
                              "        content TEXT,\n" +
                              "        created INTEGER,\n" +
                              "        modified INTEGER,\n" +
                              "        is_deleted INTEGER,\n" +
                              "        UNIQUE(topic_id, date),\n" +
                              "        FOREIGN KEY(topic_id) REFERENCES topics(id)\n" +
                              "    )\n");

            statement.execute("    CREATE TABLE IF NOT EXISTS topics (\n" +
                              "        id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                              "        name TEXT UNIQUE\n" +
                              "    )\n");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // db
    private List<String> loadTopics() {
        List<String> loadedTopics = new ArrayList<>();
        try {
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery("SELECT name FROM topics");
            while (rs.next()) {
                loadedTopics.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return loadedTopics;
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
        updateTable();
        filterTable();
    }

    private void updateTable() {
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

                try {
                    PreparedStatement ps = connection.prepareStatement(
                            "SELECT content FROM activity_log WHERE topic_id = (SELECT id FROM topics WHERE name = ?) AND date = ?");
                    ps.setString(1, topic);
                    ps.setString(2, date);
                    ResultSet rs = ps.executeQuery();

                    if (rs.next()) {
                        String content = rs.getString("content");
                        tableModel.setValueAt(content, swingTableRowNumber, day); // Hücreye content yükle
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }


            // when last
            String whenLast = updateWhenLast(topic);
            tableModel.setValueAt(whenLast, swingTableRowNumber, tableModel.getColumnCount() - 1);


        }

        tableModel.addTableModelListener(e -> {
            int row = e.getFirstRow();
            int column = e.getColumn();
            if (column > 0 && column < tableModel.getColumnCount() - 2) { // Monthly Sum ve When Last'i dışarıda tut
                String topic = (String) tableModel.getValueAt(row, 0); // İlk sütun topic ismini içeriyor
                long topicId = getTopicIdByName(topic); // Topic ismine göre ID'yi al
                if (topicId == -1) {
                    System.err.println("Topic ID bulunamadı: " + topic);
                    return; // ID bulunamazsa işlemi durdur
                }
                String date = currentDate.withDayOfMonth(column).toString();
                String content = (String) tableModel.getValueAt(row, column);
                updateCellContent(topicId, date, content); // topicId ile hücre içeriğini güncelle
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


    // db
    private String updateWhenLast(String topic) {
        String whenLastValue = "N/A"; // Varsayılan değer

        YearMonth yearMonth = YearMonth.of(currentDate.getYear(), currentDate.getMonth());
        int daysInMonth = yearMonth.lengthOfMonth();
        // When Last sütununu hesapla ve ekle
        try {
            PreparedStatement lastModifiedPs = connection.prepareStatement(
                    "SELECT MAX(date) AS max_date FROM activity_log WHERE topic_id = (SELECT id FROM topics WHERE name = ?) AND is_marked = 1"
            );
            lastModifiedPs.setString(1, topic);
            ResultSet lastModifiedRs = lastModifiedPs.executeQuery();

            if (lastModifiedRs.next()) {
                String maxDateString = lastModifiedRs.getString("max_date");
                if (maxDateString != null && !maxDateString.isEmpty()) {
                    // String değeri LocalDate'e dönüştür
                    LocalDate maxDate = LocalDate.parse(maxDateString);
                    LocalDate currentDateLocal = LocalDate.now();
                    long daysDifference = ChronoUnit.DAYS.between(maxDate, currentDateLocal);
                    daysDifference = -1 * daysDifference;
                    // Hücre değeri: Tarih ve gün farkı
                    whenLastValue = String.format("%s (%+d days)", maxDate, daysDifference);
                    //System.out.println(whenLastValue);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } catch (DateTimeParseException e) {
            System.err.println("Tarih formatı hatalı: " + e.getMessage());
        }

        return whenLastValue;

    }



    // db
    private long getTopicIdByName(String topicName) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT id FROM topics WHERE name = ?"
            );
            ps.setString(1, topicName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1; // Hata durumunda geçersiz bir değer döndür
    }

    // db
    private int getCellStatus(String topic, String date) {
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT is_marked FROM activity_log WHERE topic_id = (SELECT id FROM topics WHERE name = ?) AND date = ?");
            ps.setString(1, topic);
            ps.setString(2, date);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("is_marked");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // db
    private void toggleCellStatus(String topic, String date) {
        long currentDateAsEpoch = Instant.now().toEpochMilli();
        try {
            int status = getCellStatus(topic, date);
            if (status == 0) {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO activity_log (topic_id, date, is_marked, content, created, modified, is_deleted) " +
                                "VALUES ((SELECT id FROM topics WHERE name = ?), ?, 1, ?, ?, ?, 0) " +
                                "ON CONFLICT(topic_id, date) DO UPDATE SET is_marked = 1"
                );
                ps.setString(1, topic);
                ps.setString(2, date);
                ps.setString(3, "");
                ps.setLong(4, currentDateAsEpoch);
                ps.setLong(5, currentDateAsEpoch);
                ps.executeUpdate();
            } else if (status == 1) {
                PreparedStatement ps = connection.prepareStatement("UPDATE activity_log SET is_marked = 2, modified = ? WHERE topic_id = (SELECT id FROM topics WHERE name = ?) AND date = ?");
                ps.setLong(1, currentDateAsEpoch);
                ps.setString(2, topic);
                ps.setString(3, date);
                ps.executeUpdate();
            } else if (status == 2) {
                PreparedStatement ps = connection.prepareStatement("UPDATE activity_log SET is_marked = 3, modified = ?, is_deleted = 1 WHERE topic_id = (SELECT id FROM topics WHERE name = ?) AND date = ?");

                ps.setLong(1, currentDateAsEpoch);
                ps.setString(2, topic);
                ps.setString(3, date);
                ps.executeUpdate();
            } else if (status == 3) {
                PreparedStatement ps = connection.prepareStatement("UPDATE activity_log SET is_marked = 1, modified = ?, is_deleted = 0 WHERE topic_id = (SELECT id FROM topics WHERE name = ?) AND date = ?");

                ps.setLong(1, currentDateAsEpoch);
                ps.setString(2, topic);
                ps.setString(3, date);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private void updateTotals() {
        for (int i = 0; i < topics.size(); i++) {
            String topic = topics.get(i);
            int total = 0;
            YearMonth yearMonth = YearMonth.of(currentDate.getYear(), currentDate.getMonth());
            for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
                String date = currentDate.withDayOfMonth(day).toString();
                if (getCellStatus(topic, date) == 1) {
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
            if (newTopic != null && !newTopic.trim().isEmpty() && !topics.contains(newTopic)) {
                try {
                    PreparedStatement ps = connection.prepareStatement("INSERT INTO topics (name) VALUES (?)");
                    ps.setString(1, newTopic);
                    ps.executeUpdate();
                    topics.add(newTopic);
                    listModel.addElement(newTopic);
                    updateTable();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        });

        removeButton.addActionListener(e -> {
            String selectedTopic = topicList.getSelectedValue();
            if (selectedTopic != null) {
                int confirm = JOptionPane.showConfirmDialog(manageFrame, MessageFormat.format(bundle.getString("confirm.are.you.sure.to.delete.topic"), selectedTopic), bundle.getString("dialog.delete.confirmation.title"), JOptionPane.YES_NO_OPTION);



                if (confirm == JOptionPane.YES_OPTION) {
                    try {
                        // Önce topic_id'yi al
                        PreparedStatement getTopicIdPs = connection.prepareStatement(
                                "SELECT id FROM topics WHERE name = ?"
                        );
                        getTopicIdPs.setString(1, selectedTopic);
                        ResultSet rs = getTopicIdPs.executeQuery();
                        if (rs.next()) {
                            long topicId = rs.getLong("id");

                            // activity_log tablosundaki ilişkili kayıtları sil
                            PreparedStatement deleteActivityLogPs = connection.prepareStatement(
                                    "DELETE FROM activity_log WHERE topic_id = ?"
                            );
                            deleteActivityLogPs.setLong(1, topicId);
                            deleteActivityLogPs.executeUpdate();
                        }

                        // Ardından topic tablosundaki kaydı sil
                        PreparedStatement deleteTopicPs = connection.prepareStatement(
                                "DELETE FROM topics WHERE name = ?"
                        );
                        deleteTopicPs.setString(1, selectedTopic);
                        deleteTopicPs.executeUpdate();

                        // Listeyi ve tabloyu güncelle
                        topics.remove(selectedTopic);
                        listModel.removeElement(selectedTopic);
                        updateTable();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                }



            }
        });

        renameButton.addActionListener(e -> {
            String selectedTopic = topicList.getSelectedValue();
            if (selectedTopic != null) {
                String newTopicName = JOptionPane.showInputDialog(manageFrame, bundle.getString("dialog.label.rename.topic.name"), selectedTopic);
                if (newTopicName != null && !newTopicName.trim().isEmpty() && !topics.contains(newTopicName)) {
                    try {
                        PreparedStatement ps = connection.prepareStatement("UPDATE topics SET name = ? WHERE name = ?");
                        ps.setString(1, newTopicName);
                        ps.setString(2, selectedTopic);
                        ps.executeUpdate();
                        topics.set(topics.indexOf(selectedTopic), newTopicName);
                        listModel.set(listModel.indexOf(selectedTopic), newTopicName);
                        updateTable();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
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



    // db
    private void updateCellContent(long topicId, String date, String content) {
        long currentDateAsEpoch = Instant.now().toEpochMilli();

        try {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO activity_log (topic_id, date, is_marked, content, created, modified, is_deleted) " +
                            "VALUES (?, ?, 0, ?, ?, ?, 0) " +
                            "ON CONFLICT(topic_id, date) DO UPDATE SET content = ?, modified = ?"
            );
            ps.setLong(1, topicId);
            ps.setString(2, date);
            ps.setString(3, content); // İlk ekleme için content değeri
            ps.setLong(4, currentDateAsEpoch);
            ps.setLong(5, currentDateAsEpoch);

            ps.setString(6, content); // Eğer mevcutsa content'i güncelle
            ps.setLong(7, currentDateAsEpoch);

            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }






    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::new);
    }
}
