import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultEditorKit;

public class ChatClientGUI {
    private static final String SERVER_ADDRESS = "127.0.0.1"; //127.0.0.1 //172.20.10.2// 192.168.97.138  192.168.6.138
    private static final int PORT = 12345;
    
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    
    private JFrame frame;
    private JPanel chatPanel;
    private JScrollPane chatScrollPane;
    private JTextArea inputArea;
    private JButton sendButton;
    
    // Colors to mimic WhatsApp style
    private final Color outgoingColor = new Color(220, 248, 198);
    private final Color incomingColor = Color.WHITE;
    
    // Our own user ID from the server
    private String clientUserId = "";
    
    public ChatClientGUI() {
        setupGUI();
        connectToServer();
    }
    
    private void setupGUI() {
        frame = new JFrame("EduCon");
        frame.setSize(500, 700);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        
        // Main chat panel
        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(new Color(240, 240, 240));
        chatPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        chatScrollPane = new JScrollPane(chatPanel);
        chatScrollPane.setBorder(BorderFactory.createEmptyBorder());
        chatScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        // Multiline input area
        inputArea = new JTextArea(3, 30);
        inputArea.setFont(new Font("SansSerif", Font.PLAIN, 16));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        
        // Key bindings: ENTER sends message; SHIFT+ENTER inserts newline.
        InputMap im = inputArea.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = inputArea.getActionMap();
        im.put(KeyStroke.getKeyStroke("ENTER"), "sendMessage");
        im.put(KeyStroke.getKeyStroke("shift ENTER"), "insertNewline");
        am.put("sendMessage", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });
        am.put("insertNewline", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                inputArea.append("\n");
            }
        });
        
        JScrollPane inputScrollPane = new JScrollPane(inputArea);
        inputScrollPane.setPreferredSize(new Dimension(400, 60));
        
        sendButton = new JButton("Send");
        sendButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        sendButton.setBackground(new Color(37, 211, 102));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.addActionListener(e -> sendMessage());
        
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        inputPanel.add(inputScrollPane, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        
        frame.add(chatScrollPane, BorderLayout.CENTER);
        frame.add(inputPanel, BorderLayout.SOUTH);
        frame.setVisible(true);
    }
    
    private void connectToServer() {
        try {
            socket = new Socket(SERVER_ADDRESS, PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            addMessage("✅ Connected to chat server...", true, true);
            
            new Thread(() -> {
                try {
                    String response;
                    while ((response = in.readLine()) != null) {
                        String trimmed = response.trim();
                        System.out.println("DEBUG: Received -> \"" + trimmed + "\"");
                        
                        // If server indicates the chat history was cleared, update the UI.
                        if (trimmed.equals("Chat history cleared.")) {
                            SwingUtilities.invokeLater(() -> clearChatPanel());
                            continue;
                        }
                        
                        if (trimmed.startsWith("Your User ID: ")) {
                            clientUserId = trimmed.substring("Your User ID: ".length()).trim();
                            addMessage(trimmed, true, true);
                            System.out.println("DEBUG: clientUserId = " + clientUserId);
                        } else {
                            boolean isOutgoing = false;
                            if (!clientUserId.isEmpty() && trimmed.contains("[User " + clientUserId + "]")) {
                                isOutgoing = true;
                            }
                            if (trimmed.startsWith("You:")) {
                                isOutgoing = true;
                            }
                            if (trimmed.startsWith("AI:")) {
                                trimmed = formatAIMessage(trimmed);
                            }
                            addMessage(trimmed, isOutgoing, false);
                        }
                    }
                } catch (IOException e) {
                    addMessage("Disconnected from server.", false, true);
                }
            }).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Failed to connect to server!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void clearChatPanel() {
        chatPanel.removeAll();
        chatPanel.revalidate();
        chatPanel.repaint();
        addMessage("Chat history cleared.", true, true);
    }
    
    private String formatAIMessage(String message) {
        String content = message.substring(3).trim();
        content = content.replaceAll("\r\n?", "\n");
        int start = content.indexOf("<think>");
        int end = content.indexOf("</think>");
        if (start != -1 && end != -1 && end > start) {
            String before = content.substring(0, start);
            String thinkContent = content.substring(start + "<think>".length(), end).trim();
            String after = content.substring(end + "</think>".length());
            String replaced = before + "<span style=\"color: grey;\">" + thinkContent + "</span>" + after;
            replaced = replaced.replace("\n", "<br>");
            return "AI:<br>" + replaced;
        } else {
            return "AI:<br>" + content.replace("\n", "<br>");
        }
    }
    
    private void addMessage(final String message, final boolean isOutgoing, final boolean isSystem) {
        SwingUtilities.invokeLater(() -> {
            JPanel bubble = new JPanel();
            bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
            bubble.setBorder(new EmptyBorder(2, 2, 2, 2));
            
            String htmlMessage = "<html><div style='width:300px; word-wrap: break-word;'>" + message + "</div></html>";
            
            JTextPane msgPane = new JTextPane();
            msgPane.setContentType("text/html");
            msgPane.setText(htmlMessage);
            msgPane.setEditable(false);
            msgPane.setOpaque(false);
            msgPane.setBackground(isOutgoing ? outgoingColor : incomingColor);
            msgPane.setFont(new Font("SansSerif", Font.PLAIN, 16));
            
            InputMap im = msgPane.getInputMap();
            im.put(KeyStroke.getKeyStroke("meta C"), DefaultEditorKit.copyAction);
            im.put(KeyStroke.getKeyStroke("meta V"), DefaultEditorKit.pasteAction);
            im.put(KeyStroke.getKeyStroke("meta X"), DefaultEditorKit.cutAction);
            im.put(KeyStroke.getKeyStroke("meta A"), DefaultEditorKit.selectAllAction);
            
            if (message.startsWith("✅") || message.startsWith("⚠️") || isSystem) {
                msgPane.setFont(new Font("SansSerif", Font.ITALIC, 16));
            }
            
            bubble.add(msgPane);
            
            Dimension preferred = bubble.getPreferredSize();
            bubble.setMaximumSize(new Dimension(350, preferred.height));
            
            JPanel container = new JPanel(new FlowLayout(isOutgoing ? FlowLayout.RIGHT : FlowLayout.LEFT));
            container.setOpaque(false);
            container.add(bubble);
            
            chatPanel.add(container);
            chatPanel.add(Box.createVerticalStrut(10));
            chatPanel.revalidate();
            
            JScrollBar vertical = chatScrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }
    
    private void sendMessage() {
        String message = inputArea.getText().trim();
        if (!message.isEmpty()) {
            out.println(message);
            addMessage("You: " + message, true, false);
            inputArea.setText("");
        }
    }
    
    public static void setMacShortcuts() {
        InputMap textFieldMap = (InputMap) UIManager.get("TextField.focusInputMap");
        textFieldMap.put(KeyStroke.getKeyStroke("meta C"), DefaultEditorKit.copyAction);
        textFieldMap.put(KeyStroke.getKeyStroke("meta V"), DefaultEditorKit.pasteAction);
        textFieldMap.put(KeyStroke.getKeyStroke("meta X"), DefaultEditorKit.cutAction);
        textFieldMap.put(KeyStroke.getKeyStroke("meta A"), DefaultEditorKit.selectAllAction);
        
        InputMap textPaneMap = (InputMap) UIManager.get("TextPane.focusInputMap");
        textPaneMap.put(KeyStroke.getKeyStroke("meta C"), DefaultEditorKit.copyAction);
        textPaneMap.put(KeyStroke.getKeyStroke("meta V"), DefaultEditorKit.pasteAction);
        textPaneMap.put(KeyStroke.getKeyStroke("meta X"), DefaultEditorKit.cutAction);
        textPaneMap.put(KeyStroke.getKeyStroke("meta A"), DefaultEditorKit.selectAllAction);
    }
    
    public static void main(String[] args) {
        setMacShortcuts();
        
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("Nimbus LAF not available, using default.");
        }
        SwingUtilities.invokeLater(ChatClientGUI::new);
    }
}
