package com.turkerozturk;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class Database {

    private Connection connection;

    public Database(Connection connection) {
        this.connection = connection;
    }

    public void setupDatabase() {
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
    public List<String> loadTopics() {
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

    // db
    public String updateWhenLast(String topic, LocalDate currentDate) {
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
    public int getCellStatus(String topic, String date) {
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
    public long getTopicIdByName(String topicName) {
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
    public void toggleCellStatus(String topic, String date) {
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

    public void updateCellContent(long topicId, String date, String content) {
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

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public boolean addTopic(String newTopic, List<String> topics) {
        if (newTopic != null && !newTopic.trim().isEmpty() && !topics.contains(newTopic)) {
            try {
                PreparedStatement ps = connection.prepareStatement("INSERT INTO topics (name) VALUES (?)");
                ps.setString(1, newTopic);
                ps.executeUpdate();
                topics.add(newTopic);
                return true;

            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
        return false;
    }

    public boolean updateTopic(String newTopicName, String selectedTopic) {
        try {
            PreparedStatement ps = connection.prepareStatement("UPDATE topics SET name = ? WHERE name = ?");
            ps.setString(1, newTopicName);
            ps.setString(2, selectedTopic);
            ps.executeUpdate();
            return true;

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return false;
    }

    public String getContent(String topic, String date) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT content FROM activity_log WHERE topic_id = (SELECT id FROM topics WHERE name = ?) AND date = ?");
            ps.setString(1, topic);
            ps.setString(2, date);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String content = rs.getString("content");
                return content;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean deleteTopic(String selectedTopic) {
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
            return true;

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return false;
    }

}
