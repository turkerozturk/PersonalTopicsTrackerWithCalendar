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
