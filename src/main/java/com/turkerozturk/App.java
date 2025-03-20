package com.turkerozturk;

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
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;

public class App {

    ResourceBundle bundle = ResourceBundle.getBundle("application"); // application.properties

    private JFrame frame;


    public App() {
        frame = new JFrame(bundle.getString("window.title"));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 600);





        TopicsTrackerPanel mainPanel = new TopicsTrackerPanel();




        frame.add(mainPanel);

        //frame.add(topPanel2, BorderLayout.NORTH);

        //frame.add(scrollPane);


        frame.setVisible(true);
    }


















    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::new);
    }
}
