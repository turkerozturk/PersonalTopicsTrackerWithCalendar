package com.turkerozturk;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public class App {

    ResourceBundle bundle = ResourceBundle.getBundle("application"); // application.properties

    private JFrame frame;
    private JTable table;
    private JLabel monthLabel;

    // JLabel ekleme (tooltip yerine gösterilecek)
    private JLabel cellContentLabel;
    private LocalDate currentDate;
    private Map<String, Integer> topicTotals;
    private DefaultTableModel tableModel;
    private List<String> topics;

    private Connection connection;

    public App() {
        // Set up the database connection
        setupDatabase();

        // Initialize topics
        topics = loadTopics();

        // Initialize GUI components
        frame = new JFrame(bundle.getString("window.title"));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);

        currentDate = LocalDate.now();
        topicTotals = new HashMap<>();

        for (String topic : topics) {
            topicTotals.put(topic, 0);
        }

        cellContentLabel = new JLabel(bundle.getString("message.hover.over.a.cell"));
        JPanel topPanel = new JPanel(new BorderLayout());
        JButton prevMonthButton = new JButton(bundle.getString("button.previous.month"));
        JButton nextMonthButton = new JButton(bundle.getString("button.next.month"));
        JButton manageTopicsButton = new JButton(bundle.getString("button.topic.management"));
        monthLabel = new JLabel("", SwingConstants.CENTER);
        updateMonthLabel();

        prevMonthButton.addActionListener(e -> changeMonth(-1));
        nextMonthButton.addActionListener(e -> changeMonth(1));
        manageTopicsButton.addActionListener(e -> manageTopics());

        topPanel.add(prevMonthButton, BorderLayout.WEST);
        topPanel.add(monthLabel, BorderLayout.CENTER);

        topPanel.add(nextMonthButton, BorderLayout.EAST);
        topPanel.add(manageTopicsButton, BorderLayout.NORTH);

        JPanel topPanel2 = new JPanel(new BorderLayout());
        topPanel.add(cellContentLabel, BorderLayout.SOUTH);


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

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) ) {
                    if (e.getClickCount() == 1) { // Çift tıklama kontrolü
                        int row = table.getSelectedRow();
                        int column = table.getSelectedColumn();
                        YearMonth yearMonth = YearMonth.of(currentDate.getYear(), currentDate.getMonth());
                        if (column > 0 && column <= yearMonth.lengthOfMonth()) {
                            String topic = (String) table.getValueAt(row, 0);
                            String date = currentDate.withDayOfMonth(column).toString();
                            toggleCellStatus(topic, date);
                            updateTotals();
                            table.repaint();
                        }
                    }
                }
            }
        });


        cellContentLabel.setBounds(10, 300, 300, 30);
        cellContentLabel.setHorizontalAlignment(SwingConstants.LEFT);
        //cellContentLabel.setMinimumSize(new Dimension(20,20));

        // Tooltip eklemek için MouseMotionListener ekleyin
        table.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int column = table.columnAtPoint(e.getPoint());

                // Tooltip metnini hücre içeriği olarak ayarla
                if (row >= 0 && column >= 0) {
                    Object value = table.getValueAt(row, column);
                    table.setToolTipText(value != null ? value.toString() : ""); // uncomment if you want to see tooltip hover
                    //System.out.println(value != null ? value.toString() : "");
                    // JLabel metnini de güncelle
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
        frame.setLayout(new BorderLayout());
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);

        updateTable();

        frame.setVisible(true);
    }

    private void setupDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:calendar_tracker.db");
            Statement statement = connection.createStatement();

            // activity_log tablosunu oluştur veya güncelle
            statement.execute("""
            CREATE TABLE IF NOT EXISTS activity_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                topic TEXT,
                date TEXT,
                is_marked INTEGER,
                content TEXT,
                UNIQUE(topic, date) -- UNIQUE kısıtlaması eklendi
            )
        """);

            // topics tablosunu oluştur veya güncelle
            statement.execute("""
            CREATE TABLE IF NOT EXISTS topics (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE
            )
        """);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

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

    private void updateMonthLabel() {
        //monthLabel.setText(currentDate.getMonth().toString() + " " + currentDate.getYear());
        // Locale bilgisini application.properties dosyasından al
        String localeCode = bundle.getString("app.locale");
        Locale locale = new Locale(localeCode);



        // Ay ve yılı locale'e göre biçimlendir
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale);
        String formattedDate = currentDate.format(formatter);
        monthLabel.setText(formattedDate);
    }

    private void changeMonth(int delta) {
        currentDate = currentDate.plusMonths(delta);
        updateMonthLabel();
        updateTable();

    }

    private void updateTable() {
        YearMonth yearMonth = YearMonth.of(currentDate.getYear(), currentDate.getMonth());
        int daysInMonth = yearMonth.lengthOfMonth();

        String[] columnNames = new String[daysInMonth + 2];
        columnNames[0] = bundle.getString("cell.topic.header");
        for (int i = 1; i <= daysInMonth; i++) {
            columnNames[i] = String.valueOf(i);
        }
        columnNames[daysInMonth + 1] = bundle.getString("cell.monthly.sum");

        tableModel.setDataVector(new Object[topics.size()][columnNames.length], columnNames);

        for (int i = 0; i < topics.size(); i++) {
            String topic = topics.get(i);
            tableModel.setValueAt(topic, i, 0);

            for (int day = 1; day <= daysInMonth; day++) {
                String date = currentDate.withDayOfMonth(day).toString();

                try {
                    // Veritabanından content sütununu alın
                    PreparedStatement ps = connection.prepareStatement(
                            "SELECT content FROM activity_log WHERE topic = ? AND date = ?");
                    ps.setString(1, topic);
                    ps.setString(2, date);
                    ResultSet rs = ps.executeQuery();

                    if (rs.next()) {
                        String content = rs.getString("content");
                        tableModel.setValueAt(content, i, day); // Hücreye content yükle
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }

        tableModel.addTableModelListener(e -> {
            int row = e.getFirstRow();
            int column = e.getColumn();
            if (column > 0 && column < tableModel.getColumnCount() - 1) {
                String topic = (String) tableModel.getValueAt(row, 0);
                String date = currentDate.withDayOfMonth(column).toString();
                String content = (String) tableModel.getValueAt(row, column);
                //System.out.println(content);
                updateCellContent(topic, date, content);
            }
        });

        // adjust column widths
        SwingUtilities.invokeLater(() -> {
            table.getColumnModel().getColumn(0).setMinWidth(100);
            DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
            centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
            for (int i = 1; i < daysInMonth; i++) {
                table.getColumnModel().getColumn(i).setMinWidth(15);
                table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
            }
            table.getColumnModel().getColumn(daysInMonth + 1).setMinWidth(75);
            table.getColumnModel().getColumn(daysInMonth + 1).setCellRenderer(centerRenderer);

        });


        updateTotals();
    }

    private int getCellStatus(String topic, String date) {
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT is_marked, content FROM activity_log WHERE topic = ? AND date = ?");
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

    private void toggleCellStatus(String topic, String date) {
        try {
            int status = getCellStatus(topic, date);
            if (status == 0) {
                //PreparedStatement ps = connection.prepareStatement("INSERT INTO activity_log (topic, date, is_marked, content) VALUES (?, ?, 1, ?)");
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO activity_log (topic, date, is_marked, content) " +
                                "VALUES (?, ?, 1, ?) " +
                                "ON CONFLICT(topic, date) DO UPDATE SET is_marked = 1"
                );
                ps.setString(1, topic);
                ps.setString(2, date);
                ps.setString(3, "");








                ps.executeUpdate(); // burada hata veriyor turker
            } else if (status == 1) {
                PreparedStatement ps = connection.prepareStatement("UPDATE activity_log SET is_marked = 2 WHERE topic = ? AND date = ?");
                ps.setString(1, topic);
                ps.setString(2, date);
                ps.executeUpdate();
            } else {
                PreparedStatement ps = connection.prepareStatement("DELETE FROM activity_log WHERE topic = ? AND date = ?");
                ps.setString(1, topic);
                ps.setString(2, date);
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
            tableModel.setValueAt(total, i, tableModel.getColumnCount() - 1);
        }
    }

    private void manageTopics() {
        JFrame manageFrame = new JFrame("Konu Yönetimi");
        manageFrame.setSize(400, 300);
        manageFrame.setLayout(new BorderLayout());

        DefaultListModel<String> listModel = new DefaultListModel<>();
        topics.forEach(listModel::addElement);
        JList<String> topicList = new JList<>(listModel);

        JButton addButton = new JButton(bundle.getString("button.add.topic"));
        JButton removeButton = new JButton(bundle.getString("button.delete.topic"));
        JButton renameButton = new JButton(bundle.getString("button.rename.topic"));

        addButton.addActionListener(e -> {
            // Özel düğme metinlerini belirle
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
                        PreparedStatement ps = connection.prepareStatement("DELETE FROM topics WHERE name = ?");
                        ps.setString(1, selectedTopic);
                        ps.executeUpdate();
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

    private void updateCellContent(String topic, String date, String content) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO activity_log (topic, date, is_marked, content) " +
                            "VALUES (?, ?, 0, ?) " +
                            "ON CONFLICT(topic, date) DO UPDATE SET content = ?"
            );
            ps.setString(1, topic);
            ps.setString(2, date);
            ps.setString(3, content); // İlk ekleme için content değeri
            ps.setString(4, content); // Eğer mevcutsa content'i güncelle
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::new);
    }
}
