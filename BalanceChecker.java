package balance_checker;

import java.net.*;
import java.io.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.*;
import javax.swing.*;
import com.gargoylesoftware.htmlunit.*;
import com.gargoylesoftware.htmlunit.html.*;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit.*;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.Date;

public class BalanceChecker {
    
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    static final ConfigGUI config = new ConfigGUI();
    static final TrayIcon trayIcon = createTrayIcon("images/logo.png");
    static final String configValues[] = readConfig("config.txt");

    public static void main(String[] args) {
        /* Get update frequency */
        long frequency;
        try {
            frequency = Long.parseLong(configValues[1]);
            /* Get time units for update frequency */
            switch (configValues[2]) {
                case "s":
                    break;
                case "m":
                    frequency *= 60;
                    break;
                case "h":
                    frequency *= 60 * 60;
                    break;
            }
        } catch (NumberFormatException exc) {
            System.out.println(exc.getMessage());
            /* If frequency couldn't be parsed, let it be 1 hour */
            frequency = 3600;
        }
        
        final String login = configValues[3];
        final char[] password;
        if(configValues[4] != null) {
            password = configValues[4].toCharArray();
        } else {
            password = null;
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                GUI();
            }
        });
        
        final Runnable checker;
        checker = new Runnable() {
            @Override
            public void run() {
                try {
                    if (config.getLogin().isEmpty() || config.getPassword().length == 0) {
                        getBalance(login, password);
                    } else {
                        getBalance(config.getLogin(), config.getPassword());
                    }
                } catch (Exception exc) {
                    System.out.println(exc.getMessage());
                }
            }
        };
        
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        long difference;
        Date date;
        try {
            date = format.parse(configValues[0]);
            //System.out.println(date.toString());
        } catch (ParseException | NullPointerException exc) {
            System.out.println(exc.getMessage());
            /* If date couldn't be parsed, let it be the current date */
            date = new Date();
        }
        difference = date.getTime() - System.currentTimeMillis();
        difference = TimeUnit.MILLISECONDS.toSeconds(difference);
        
        /* This is the case when date couldn't be parsed */
        if(difference == 0) {
            difference = 20;
        /*
         * This is a workaround for the Java bug:
         * Java considers Moscow time zone to be UTC+4.
         * However, it is UTC+3.
         */
        } else if ("Europe/Moscow".equals(TimeZone.getDefault().getID())) {
            difference += 3600;
        }
        
        final ScheduledFuture<?> checkerHandle = 
                scheduler.scheduleAtFixedRate(checker, difference, frequency, TimeUnit.SECONDS);
    }
    
    protected static String[] readConfig(String path) {
        String configParams[] = new String[5];
        Path configPath = FileSystems.getDefault().getPath(path);
        try (BufferedReader br = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            String inputLine;
            while ((inputLine = br.readLine()) != null) {
                if (inputLine.startsWith("firstRun")) {
                    int index = inputLine.indexOf('=');
                    configParams[0] = inputLine.substring(index + 1).trim();
                } else if (inputLine.startsWith("updateFrequency")) {
                    configParams[1] = inputLine.replaceAll("[^\\d]", "");
                    configParams[2] = inputLine.replaceAll("[^smh]", "");
                } else if (inputLine.startsWith("login")) {
                    int index = inputLine.indexOf('=');
                    configParams[3] = inputLine.substring(index + 1).trim();
                } else if (inputLine.startsWith("password")) {
                    int index = inputLine.indexOf('=');
                    configParams[4] = inputLine.substring(index + 1).trim();
                }
            }
        } catch (IOException exc) {
            System.out.println(exc.getMessage());
        }
        return configParams;
    }
    
    protected static TrayIcon createTrayIcon(String path) {
        URL imageURL = BalanceChecker.class.getResource(path);
        if (imageURL == null) {
            System.out.println("Resource not found: " + path);
            return null;
        } else {
            try {
                BufferedImage image = ImageIO.read(imageURL);
                int trayIconWidth = new TrayIcon(image).getSize().width;
                return new TrayIcon(image.getScaledInstance(trayIconWidth, -1, Image.SCALE_SMOOTH));
            } catch (IOException exc) {
                System.out.println(exc.getMessage());
            }
            return null;
        }
    }
    
    public static void GUI() {
        if (!SystemTray.isSupported()) {
            System.out.println("System Tray is not supported");
            return;
        }
        
        final PopupMenu menu = new PopupMenu();
        final SystemTray tray = SystemTray.getSystemTray();
        
        MenuItem balanceItem = new MenuItem("Balance");
        MenuItem configItem = new MenuItem("Config");
        MenuItem exitItem = new MenuItem("Exit");
        
        menu.add(balanceItem);
        menu.add(configItem);
        menu.addSeparator();
        menu.add(exitItem);
        
       trayIcon.setPopupMenu(menu);
        
        try {
            tray.add(trayIcon);
        } catch (AWTException exc) {
            System.out.println(exc.getMessage());
            System.exit(0);
        }
        
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1)
                    trayIcon.displayMessage("Balance checker", "Money on your provider's account",
                                            TrayIcon.MessageType.NONE);
            }
        });
        
        balanceItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    if (config.getLogin().isEmpty() || config.getPassword().length == 0) {
                        getBalance(configValues[3], configValues[4].toCharArray());
                    } else {
                        getBalance(config.getLogin(), config.getPassword());
                    }
                } catch (Exception exc) {
                    System.out.println(exc.getMessage());
                }
            }
        });
        
        configItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                config.setVisible(true);
            }
        });
        
        exitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tray.remove(trayIcon);
                System.exit(0);
            }
        });
        
    }
    
    /*
     * This function utilizes library HtmlUnit to fill login and password forms
     * on the web page, send the request to server, and then get the response.
     */
    public static void getBalance(String login, char[] password) throws Exception {
        final WebClient webClient = new WebClient();
        final HtmlPage page = webClient.getPage("http://cabinet.telecom.mipt.ru");
        
        final DomElement loginElement = page.getElementById("login");
        loginElement.setAttribute("value", login);
        
        final DomElement passwordElement = page.getElementById("password");
        passwordElement.setAttribute("value", new String(password));
        
        final HtmlElement button = page.getHtmlElementByAccessKey('s');
        HtmlPage nextpage = (HtmlPage) button.click();

        String xmlString = nextpage.asXml();
        InputStream is = new ByteArrayInputStream(xmlString.getBytes());
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String inputLine;
            while ((inputLine = br.readLine()) != null) {
                if (inputLine.contains("Текущий баланс")) {
                    /* Remove everything except numbers */
                    inputLine = inputLine.replaceAll("[^\\d]", "");
                    /*
                     * Try to parse server response as double.
                     * If successful, divide it by 100 because the dividing
                     * dot or comma have been removed from the string.
                     * If the string cannot be parsed as double, it contains
                     * an error message from the server.
                     */
                    try {
                        double balance = Double.parseDouble(inputLine);
                        balance /= 100;
                        trayIcon.displayMessage("Your balance", balance + " руб.",
                                                TrayIcon.MessageType.NONE);
                    } catch(NumberFormatException exc) {
                        System.out.println(exc.getMessage());
                        trayIcon.displayMessage("Oops!", inputLine,
                                                TrayIcon.MessageType.ERROR);
                    }
                    return;
                } else if (inputLine.contains("error")) {
                    /* Incorrect login and/or password */
                    trayIcon.displayMessage("Oops!", br.readLine().trim(),
                                            TrayIcon.MessageType.ERROR);
                }
            }
        }
    }
    
}
