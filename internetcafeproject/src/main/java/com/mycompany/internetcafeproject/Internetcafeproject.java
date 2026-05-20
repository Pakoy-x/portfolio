package com.mycompany.internetcafeproject;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

// ---------------- DATABASE ----------------
class Database {
    private static Connection conn;

    // Connect to SQLite database
    public static Connection getConnection() {
        if (conn == null) {
            try {
                Class.forName("org.sqlite.JDBC");
                conn = DriverManager.getConnection("jdbc:sqlite:internet_cafe.db");
                initializeDatabase();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return conn;
    }

    // Initialize tables if they don't exist
    private static void initializeDatabase() throws SQLException {
        Statement stmt = conn.createStatement();

        // Users table
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT UNIQUE," +
                "password TEXT," +
                "role TEXT)");

        // Computers table
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS computers (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "status TEXT)");

        // Billing table
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS billing (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER," +
                "computer_id INTEGER," +
                "start_time TEXT," +
                "end_time TEXT," +
                "duration REAL," +
                "total REAL)");

        // Add 10 computers if empty
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM computers");
        if (rs.next() && rs.getInt(1) == 0) {
            for (int i = 0; i < 10; i++) {
                stmt.executeUpdate("INSERT INTO computers (status) VALUES ('available')");
            }
        }
    }
}

// ---------------- USER ----------------
class User {
    public int id;
    public String username, password, role;

    public User(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    // Register new user
    public boolean register() {
        try {
            Connection conn = Database.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (username, password, role) VALUES (?, ?, ?)");
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, role);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false; // Username already exists
        }
    }

    // Login user
    public static User login(String username, String password, String role) {
        try {
            Connection conn = Database.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM users WHERE username=? AND password=? AND role=?");
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, role);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                User u = new User(rs.getString("username"), rs.getString("password"), rs.getString("role"));
                u.id = rs.getInt("id");
                return u;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}

// ---------------- COMPUTER ----------------
class Computer {
    public int id;
    public String status;

    public Computer(int id, String status) {
        this.id = id;
        this.status = status;
    }

    // Get all computers from database
    public static Computer[] getAll() {
        ArrayList<Computer> list = new ArrayList<>();
        try {
            Connection conn = Database.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM computers");

            while (rs.next()) {
                list.add(new Computer(rs.getInt("id"), rs.getString("status")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list.toArray(new Computer[0]);
    }

    // Update computer status
    public static void updateStatus(int id, String status) {
        try {
            Connection conn = Database.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "UPDATE computers SET status=? WHERE id=?");
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

// ---------------- BILLING ----------------
class Billing {
    public int userId, computerId;
    public LocalDateTime startTime, endTime;
    public double duration, total;

    public Billing(int userId, int computerId) {
        this.userId = userId;
        this.computerId = computerId;
        this.startTime = LocalDateTime.now();
    }

    // End session and calculate total
    public void endSession(double ratePerHour) {
        this.endTime = LocalDateTime.now();
        this.duration = ChronoUnit.MINUTES.between(startTime, endTime) / 60.0;
        this.total = duration * ratePerHour;

        try {
            Connection conn = Database.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO billing (user_id, computer_id, start_time, end_time, duration, total) " +
                            "VALUES (?, ?, ?, ?, ?, ?)"
            );
            ps.setInt(1, userId);
            ps.setInt(2, computerId);
            ps.setString(3, startTime.toString());
            ps.setString(4, endTime.toString());
            ps.setDouble(5, duration);
            ps.setDouble(6, total);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

// ---------------- MAIN APPLICATION ----------------
public class Internetcafeproject {
    JFrame frame;
    User currentUser;
    Billing currentBilling;
    final double RATE_PER_HOUR = 2.5; // Billing rate

    public Internetcafeproject() {
        frame = new JFrame("Internet Cafe Project");
        frame.setSize(900, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        showWelcomeScreen();
        frame.setVisible(true);
    }

    // ---------------- WELCOME SCREEN ----------------
    private void showWelcomeScreen() {
        frame.getContentPane().removeAll();
        frame.setLayout(new GridBagLayout());
        frame.getContentPane().setBackground(new Color(44, 62, 80));

        JLabel title = new JLabel("Welcome to Internet Cafe");
        title.setFont(new Font("SansSerif", Font.BOLD, 32));
        title.setForeground(Color.WHITE);

        JButton customerBtn = new JButton("Customer Login");
        JButton adminBtn = new JButton("Admin Login");

        styleButton(customerBtn);
        styleButton(adminBtn);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(20,0,20,0);
        gbc.gridx = 0; gbc.gridy = 0;
        frame.add(title, gbc);
        gbc.gridy = 1; frame.add(customerBtn, gbc);
        gbc.gridy = 2; frame.add(adminBtn, gbc);

        customerBtn.addActionListener(e -> showLoginScreen("customer"));
        adminBtn.addActionListener(e -> showLoginScreen("admin"));

        frame.revalidate();
        frame.repaint();
    }

    // ---------------- STYLE BUTTON ----------------
    private void styleButton(JButton btn) {
        btn.setBackground(new Color(52, 152, 219));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 20));
        btn.setPreferredSize(new Dimension(250, 50));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(new Color(41, 128, 185)); }
            public void mouseExited(MouseEvent e) { btn.setBackground(new Color(52, 152, 219)); }
        });
    }

    // ---------------- LOGIN SCREEN ----------------
    private void showLoginScreen(String role) {
        frame.getContentPane().removeAll();
        frame.setLayout(new GridBagLayout());
        frame.getContentPane().setBackground(new Color(44, 62, 80));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10,10,10,10);

        JLabel label = new JLabel(role.substring(0,1).toUpperCase()+role.substring(1)+" Login");
        label.setFont(new Font("SansSerif", Font.BOLD, 26));
        label.setForeground(Color.WHITE);
        gbc.gridx=0; gbc.gridy=0; gbc.gridwidth=2;
        frame.add(label, gbc);

        gbc.gridwidth=1;
        JLabel userLabel = new JLabel("Username:"); userLabel.setForeground(Color.WHITE);
        gbc.gridx=0; gbc.gridy=1; frame.add(userLabel, gbc);
        JTextField userField = new JTextField(15); gbc.gridx=1; frame.add(userField, gbc);

        JLabel passLabel = new JLabel("Password:"); passLabel.setForeground(Color.WHITE);
        gbc.gridx=0; gbc.gridy=2; frame.add(passLabel, gbc);
        JPasswordField passField = new JPasswordField(15); gbc.gridx=1; frame.add(passField, gbc);

        JButton loginBtn = new JButton("Login");
        JButton registerBtn = new JButton("Register");
        styleButton(loginBtn); styleButton(registerBtn);
        gbc.gridx=0; gbc.gridy=3; frame.add(loginBtn, gbc);
        gbc.gridx=1; frame.add(registerBtn, gbc);

        loginBtn.addActionListener(e -> {
            User u = User.login(userField.getText(), new String(passField.getPassword()), role);
            if(u!=null){
                currentUser = u;
                if(role.equals("customer")) showCustomerComputers();
                else JOptionPane.showMessageDialog(frame,"Admin dashboard not implemented yet!");
            } else JOptionPane.showMessageDialog(frame,"Invalid credentials");
        });

        registerBtn.addActionListener(e -> showRegisterScreen(role));

        frame.revalidate();
        frame.repaint();
    }

    // ---------------- REGISTER SCREEN ----------------
    private void showRegisterScreen(String role) {
        frame.getContentPane().removeAll();
        frame.setLayout(new GridBagLayout());
        frame.getContentPane().setBackground(new Color(44, 62, 80));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10,10,10,10);

        JLabel label = new JLabel("Register " + role);
        label.setFont(new Font("SansSerif", Font.BOLD, 26));
        label.setForeground(Color.WHITE);
        gbc.gridx=0; gbc.gridy=0; gbc.gridwidth=2;
        frame.add(label, gbc);

        gbc.gridwidth=1;
        JLabel userLabel = new JLabel("Username:"); userLabel.setForeground(Color.WHITE);
        gbc.gridx=0; gbc.gridy=1; frame.add(userLabel, gbc);
        JTextField userField = new JTextField(15); gbc.gridx=1; frame.add(userField, gbc);

        JLabel passLabel = new JLabel("Password:"); passLabel.setForeground(Color.WHITE);
        gbc.gridx=0; gbc.gridy=2; frame.add(passLabel, gbc);
        JPasswordField passField = new JPasswordField(15); gbc.gridx=1; frame.add(passField, gbc);

        JLabel rePassLabel = new JLabel("Re-enter Password:"); rePassLabel.setForeground(Color.WHITE);
        gbc.gridx=0; gbc.gridy=3; frame.add(rePassLabel, gbc);
        JPasswordField rePassField = new JPasswordField(15); gbc.gridx=1; frame.add(rePassField, gbc);

        JButton createBtn = new JButton("Create"); JButton backBtn = new JButton("Back");
        styleButton(createBtn); styleButton(backBtn);
        gbc.gridx=0; gbc.gridy=4; frame.add(createBtn, gbc);
        gbc.gridx=1; frame.add(backBtn, gbc);

        createBtn.addActionListener(e -> {
            if(!new String(passField.getPassword()).equals(new String(rePassField.getPassword()))){
                JOptionPane.showMessageDialog(frame,"Passwords do not match"); return;
            }
            User u = new User(userField.getText(), new String(passField.getPassword()), role);
            if(u.register()){
                JOptionPane.showMessageDialog(frame,"Account created! Login now."); showLoginScreen(role);
            } else JOptionPane.showMessageDialog(frame,"Username already exists");
        });

        backBtn.addActionListener(e -> showLoginScreen(role));

        frame.revalidate();
        frame.repaint();
    }

    // ---------------- CUSTOMER COMPUTERS ----------------
    private void showCustomerComputers() {
        frame.getContentPane().removeAll();
        frame.setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel(); header.setBackground(new Color(52, 73, 94));
        JLabel title = new JLabel("Select a Computer");
        title.setFont(new Font("SansSerif", Font.BOLD, 28)); title.setForeground(Color.WHITE);
        header.add(title); frame.add(header, BorderLayout.NORTH);

        // Computers Grid
        JPanel compPanel = new JPanel(); compPanel.setBackground(new Color(44, 62, 80));
        compPanel.setLayout(new GridLayout(2,5,15,15));
        Computer[] comps = Computer.getAll();
        ImageIcon compIcon = new ImageIcon("computer.png"); // Add your image

        for(Computer c: comps){
            JLabel compLabel = new JLabel();
            compLabel.setHorizontalTextPosition(JLabel.CENTER);
            compLabel.setVerticalTextPosition(JLabel.BOTTOM);
            compLabel.setIcon(compIcon); compLabel.setText("Computer "+c.id);
            compLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
            compLabel.setForeground(Color.WHITE);
            compLabel.setOpaque(true); compLabel.setBackground(new Color(52,73,94));
            compLabel.setHorizontalAlignment(JLabel.CENTER); compLabel.setVerticalAlignment(JLabel.CENTER);
            compLabel.setBorder(new LineBorder(Color.GRAY, 3, true));

            // Set border color by status
            switch(c.status){
                case "available" -> compLabel.setBorder(new LineBorder(new Color(46, 204, 113),3,true));
                case "occupied" -> compLabel.setBorder(new LineBorder(new Color(231,76,60),3,true));
                case "maintenance" -> compLabel.setBorder(new LineBorder(new Color(241,196,15),3,true));
                case "removed" -> compLabel.setBorder(new LineBorder(Color.GRAY,3,true));
            }

            // Clickable only if available
            if(c.status.equals("available")){
                compLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                compLabel.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent e){ startBilling(c.id); }
                    public void mouseEntered(MouseEvent e){ compLabel.setBackground(new Color(41,128,185)); }
                    public void mouseExited(MouseEvent e){ compLabel.setBackground(new Color(52,73,94)); }
                });
            }
            compPanel.add(compLabel);
        }

        frame.add(compPanel, BorderLayout.CENTER);

        JPanel footer = new JPanel(); footer.setBackground(new Color(52,73,94));
        JButton backBtn = new JButton("Back"); styleButton(backBtn);
        backBtn.addActionListener(e -> showWelcomeScreen());
        footer.add(backBtn);
        frame.add(footer, BorderLayout.SOUTH);

        frame.revalidate();
        frame.repaint();
    }

    // ---------------- START BILLING ----------------
    private void startBilling(int computerId){
        Computer.updateStatus(computerId,"occupied");
        currentBilling = new Billing(currentUser.id, computerId);
        JOptionPane.showMessageDialog(frame,"Computer "+computerId+" session started!");
        showCustomerComputers();
    }

    // ---------------- MAIN ----------------                                                       
    public static void main(String[] args) {
        new Internetcafeproject();
    }
}
