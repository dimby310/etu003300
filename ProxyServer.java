package server;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ProxyServer {

    private static int PROXY_PORT;
    private static String SERVER_IP;
    private static int APACHE_PORT;
    private static int TOMCAT_PORT;
    public static Map<String, String> cache = new ConcurrentHashMap<>();
    private static JTextArea outputArea;
    private static ServerSocket serverSocket;
    private static boolean isServerRunning = false;
    private static Thread serverThread;
    private static ThreadPoolExecutor threadPoolExecutor;
    private static JProgressBar progressBar;


    public static void main(String[] args) {
        loadConfig();
        createGUI();
    }

    

    private static void loadConfig() {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream("proxy.config")) {
            properties.load(fis);
            PROXY_PORT = Integer.parseInt(properties.getProperty("proxy_port"));
            SERVER_IP = properties.getProperty("server_ip");
            APACHE_PORT = Integer.parseInt(properties.getProperty("apache_port"));
            TOMCAT_PORT = Integer.parseInt(properties.getProperty("tomcat_port"));
            System.out.println("Configuration chargée avec succès !");
        } catch (IOException | NumberFormatException e) {
            // Afficher une alerte
            JOptionPane.showMessageDialog(null, "Erreur lors du chargement du fichier de configuration : " + e.getMessage(),
                                          "Erreur de configuration", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
    
    private static void createGUI() {
        JFrame frame = new JFrame("Proxy Cache Server");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLayout(new BorderLayout());

        // Application d'un Look and Feel moderne
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Création des panneaux pour chaque onglet
        JPanel panelServeur = createServerPanel();
        JPanel panelCache = createCachePanel();
        JPanel panelConfig = createConfigPanel();

        // Création du JTabbedPane pour les onglets
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Serveur", panelServeur);
        tabbedPane.addTab("Cache", panelCache);
        tabbedPane.addTab("Configuration", panelConfig);

        // Ajouter le JTabbedPane à la fenêtre principale
        frame.add(tabbedPane, BorderLayout.CENTER);

        frame.setVisible(true);
    }

    private static JPanel createServerPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(outputArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        panel.add(progressBar, BorderLayout.NORTH);

        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new GridLayout(2, 3, 10, 10));
        controlPanel.setBackground(new Color(245, 245, 245));

        JButton btnStartServer = new JButton("Démarrer Serveur");
        btnStartServer.setBackground(new Color(52, 152, 219));
        btnStartServer.setForeground(Color.WHITE);
        btnStartServer.addActionListener(e -> startServer());

        JButton btnStopServer = new JButton("Arrêter Serveur");
        btnStopServer.setBackground(new Color(231, 76, 60));
        btnStopServer.addActionListener(e -> stopServer());

        controlPanel.add(btnStartServer);
        controlPanel.add(btnStopServer);

        panel.add(controlPanel, BorderLayout.SOUTH);
        return panel;
    }

    private static JPanel createCachePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new GridLayout(3, 1, 10, 10));
        controlPanel.setBackground(new Color(245, 245, 245));

        JButton btnClearCache = new JButton("Vider Cache");
        btnClearCache.addActionListener(e -> ProxyCache.clearCache(cache, outputArea));

        JButton btnListCache = new JButton("Lister Cache");
        btnListCache.addActionListener(e -> ProxyCache.listCacheElements(cache, outputArea));

        JButton btnAutoClear = new JButton("Auto Vider Cache");
        btnAutoClear.addActionListener(e -> {
            String time = JOptionPane.showInputDialog("Entrez le délai (en secondes) :");
            long delay = Long.parseLong(time);
            ProxyCache.autoClearCache(cache, delay, TimeUnit.SECONDS, outputArea);
        });

        controlPanel.add(btnClearCache);
        controlPanel.add(btnListCache);
        controlPanel.add(btnAutoClear);

        panel.add(controlPanel, BorderLayout.SOUTH);
        return panel;
    }

    private static JPanel createConfigPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
    
        // Zone de texte pour afficher la configuration
        JTextArea configArea = new JTextArea();
        configArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(configArea);
        panel.add(scrollPane, BorderLayout.CENTER);
    
        // Panneau pour les contrôles
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new GridLayout(3, 2, 10, 10)); // 3 lignes et 2 colonnes
    
        // Champs de texte pour les ports
        JLabel apachePortLabel = new JLabel("Port Apache:");
        JTextField apachePortField = new JTextField(String.valueOf(APACHE_PORT));  // Valeur initiale
        JLabel tomcatPortLabel = new JLabel("Port Tomcat:");
        JTextField tomcatPortField = new JTextField(String.valueOf(TOMCAT_PORT));  // Valeur initiale
    
        // Ajouter les composants au panneau de contrôle
        controlPanel.add(apachePortLabel);
        controlPanel.add(apachePortField);
        controlPanel.add(tomcatPortLabel);
        controlPanel.add(tomcatPortField);
    
        // Bouton pour appliquer la configuration
        JButton btnSetPorts = new JButton("Configurer les ports");
        btnSetPorts.addActionListener(e -> {
            try {
                // Récupérer les nouveaux ports à partir des champs de texte
                int newApachePort = Integer.parseInt(apachePortField.getText());
                int newTomcatPort = Integer.parseInt(tomcatPortField.getText());
    
                // Mettre à jour les variables de port
                APACHE_PORT = newApachePort;
                TOMCAT_PORT = newTomcatPort;
    
                // Mettre à jour le fichier de configuration proxy.config
                updateConfigFile();
    
                // Afficher la mise à jour dans l'interface
                configArea.append("Ports Apache et Tomcat mis à jour : " + APACHE_PORT + ", " + TOMCAT_PORT + "\n");
    
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(null, "Les ports doivent être des nombres valides.", "Erreur de configuration", JOptionPane.ERROR_MESSAGE);
            }
        });
    
        // Ajouter le bouton au panneau de contrôle
        controlPanel.add(btnSetPorts);
        
        // Ajouter le panneau de contrôle au panneau principal
        panel.add(controlPanel, BorderLayout.SOUTH);
    
        return panel;
    }
    


    
    private static void startServer() {
        if (isServerRunning) {
            outputArea.append("Le serveur est déjà en cours d'exécution.\n");
            return;
        }
    
        progressBar.setIndeterminate(true);  // Barre de progression indéterminée pendant le démarrage
    
        serverThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PROXY_PORT)) {
                ProxyServer.serverSocket = serverSocket;
                isServerRunning = true;
                outputArea.append("Serveur démarré sur le port " + PROXY_PORT + "\n");
                progressBar.setIndeterminate(false);  // Arrêter la barre de progression indéterminée
    
                while (isServerRunning) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleClient(clientSocket)).start();
                }
            } catch (IOException e) {
                outputArea.append("Erreur lors du démarrage du serveur : " + e.getMessage() + "\n");
            }
        });
    
        serverThread.start();
    }
    
    private static void stopServer() {
        if (!isServerRunning) {
            outputArea.append("Le serveur n'est pas en cours d'exécution.\n");
            return;
        }
    
        try {
            isServerRunning = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (serverThread != null && serverThread.isAlive()) {
                serverThread.interrupt();
            }
            if (threadPoolExecutor != null) {
                threadPoolExecutor.shutdown();  // Arrêt du pool de threads
                outputArea.append("Le serveur a arrêté le pool de threads.\n");
            }
            outputArea.append("Serveur arrêté.\n");
        } catch (IOException e) {
            outputArea.append("Erreur lors de l'arrêt du serveur : " + e.getMessage() + "\n");
        }
    }
    

    private static void handleClient(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String request = in.readLine();
            if (request == null || request.trim().isEmpty()) {
                return;
            }

            if (cache.containsKey(request)) {
                outputArea.append("Requête récupérée dans le cache (" + request + ")\n");
                out.println(cache.get(request));
            } else {
                outputArea.append("Requête non trouvée dans le cache. Recherche au serveur Apache...\n");
                String response = getAnswerFromServer(SERVER_IP, APACHE_PORT, request);
                if (response == null) {
                    out.println("HTTP/1.1 404 Not Found\r\nContent-Type: text/html; charset=UTF-8\r\n\r\nPage non trouvée");
                } else {
                    cache.put(request, response);
                    out.println(response);
                }
            }
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getAnswerFromServer(String serverIp, int serverPort, String requestLine) {
        try (Socket serverSocket = new Socket(serverIp, serverPort);
             BufferedReader serverIn = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
             PrintWriter serverOut = new PrintWriter(serverSocket.getOutputStream(), true)) {
    
            StringBuilder requestBuilder = new StringBuilder();
            requestBuilder.append(requestLine).append("\r\n");
            requestBuilder.append("Host: ").append(serverIp).append("\r\n");
            requestBuilder.append("Connection: close\r\n");
            requestBuilder.append("\r\n");
    
            serverOut.print(requestBuilder.toString());
            serverOut.flush();
    
            String serverResponse;
            StringBuilder responseBuilder = new StringBuilder();
    
            while ((serverResponse = serverIn.readLine()) != null) {
                responseBuilder.append(serverResponse).append("\n");
            }
    
            return responseBuilder.toString();
        } catch (IOException e) {
            // Affichage d'une alerte en cas d'erreur de connexion
            JOptionPane.showMessageDialog(null, "Erreur lors de la connexion au serveur : " + e.getMessage(),
                                          "Erreur de connexion", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    // Méthode pour mettre à jour le fichier de configuration proxy.config
private static void updateConfigFile() {
    Properties properties = new Properties();
    try (FileInputStream fis = new FileInputStream("proxy.config")) {
        properties.load(fis);
        
        // Modifier les propriétés des ports
        properties.setProperty("apache_port", String.valueOf(APACHE_PORT));
        properties.setProperty("tomcat_port", String.valueOf(TOMCAT_PORT));

        // Sauvegarder les modifications dans le fichier de configuration
        try (FileOutputStream fos = new FileOutputStream("proxy.config")) {
            properties.store(fos, "Mise à jour des ports");
        }

        System.out.println("Configuration mise à jour dans le fichier.");
    } catch (IOException e) {
        System.err.println("Erreur lors de la mise à jour du fichier de configuration : " + e.getMessage());
    }
}

    
}
