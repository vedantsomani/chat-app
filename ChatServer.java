import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class ChatServer {
    private static final int PORT = 12345;
    // Clients keyed by userId; this map is used for both private and group messaging.
    private static final Map<Integer, ClientHandler> clients = new HashMap<>();
    private static int userIdCounter = 1000;
    private static final String SECRET_KEY = "MySecretKey12345"; // Must be 16 characters for AES

    public static void main(String[] args) {
        System.out.println("Chat server started on port " + PORT);
        new File("ChatLogs").mkdir(); // Create directory for chat logs

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                int userId = userIdCounter++;
                ClientHandler clientHandler = new ClientHandler(socket, userId);
                synchronized(clients) {
                    clients.put(userId, clientHandler);
                }
                new Thread(clientHandler).start();
                System.out.println("User " + userId + " connected.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Sends a private message from sender to target user and logs for both.
    public static void sendMessageTo(int targetUserId, String message, int senderId) {
        String recipientMessage = "[User " + senderId + "] " + message;
        String senderMessage = "You: (@" + targetUserId + ") " + message;

        String encryptedRecipientMsg = encrypt(recipientMessage);
        String encryptedSenderMsg = encrypt(senderMessage);

        ClientHandler targetClient;
        synchronized(clients) {
            targetClient = clients.get(targetUserId);
        }
        if (targetClient != null) {
            targetClient.sendMessage(decrypt(encryptedRecipientMsg));
            logMessage(targetUserId, encryptedRecipientMsg);
        } else {
            ClientHandler sender;
            synchronized(clients) {
                sender = clients.get(senderId);
            }
            if (sender != null) {
                sender.sendMessage("User " + targetUserId + " is not online.");
            }
        }
        logMessage(senderId, encryptedSenderMsg);
    }

    // Broadcasts a group message to all connected clients.
    public static void broadcastMessage(String message, int senderId) {
        String senderPrefix = "You: ";
        String recipientPrefix = "[User " + senderId + "]: ";
        String encryptedMsgForLog = encrypt("[" + senderId + "] " + message);

        synchronized(clients) {
            for (Map.Entry<Integer, ClientHandler> entry : clients.entrySet()) {
                int uid = entry.getKey();
                ClientHandler client = entry.getValue();
                if (uid == senderId) {
                    client.sendMessage(senderPrefix + message);
                } else {
                    client.sendMessage(recipientPrefix + message);
                }
                // Log the message for every client in the group chat.
                logMessage(uid, encryptedMsgForLog);
            }
        }
    }

    // Logs an encrypted message to the user-specific file.
    public static void logMessage(int userId, String message) {
        String fileName = "ChatLogs/user_" + userId + ".txt";
        try (FileWriter writer = new FileWriter(fileName, true)) {
            writer.write(message + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Clears the chat history file for the given user by deleting it.
    public static void clearChatHistory(int userId) {
        new File("ChatLogs/user_" + userId + ".txt").delete();
    }

    // Encrypts a string with AES.
    public static String encrypt(String str) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            SecretKey key = new SecretKeySpec(SECRET_KEY.getBytes(), "AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(cipher.doFinal(str.getBytes()));
        } catch (Exception e) {
            e.printStackTrace();
            return str;
        }
    }

    // Decrypts a string with AES.
    public static String decrypt(String str) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            SecretKey key = new SecretKeySpec(SECRET_KEY.getBytes(), "AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return new String(cipher.doFinal(Base64.getDecoder().decode(str)));
        } catch (Exception e) {
            e.printStackTrace();
            return str;
        }
    }

    // Calls the local Ollama AI model (deepseek-r1:1.5b or qwen2-math:1.5b)
    public static String callAI(String prompt, String model) {
        try {
            ProcessBuilder pb = new ProcessBuilder("ollama", "run", model, prompt);
            Process process = pb.start();
            process.getOutputStream().close();

            BufferedReader stdReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = stdReader.readLine()) != null) {
                output.append(line).append(" ");
            }

            process.waitFor();
            return output.toString().trim();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error calling AI model.";
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private int userId;

        public ClientHandler(Socket socket, int userId) {
            this.socket = socket;
            this.userId = userId;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Send user ID and chat history to client.
                out.println("Your User ID: " + userId);
                sendChatHistory();

                String input;
                while ((input = in.readLine()) != null) {
                    String trimmedInput = input.trim();

                    // Check for the clear command.
                    if (trimmedInput.equals("\\clear")) {
                        ChatServer.clearChatHistory(userId);
                        continue;
                    }

                    // AI model selection: @math for math requests, @ai for general AI requests.
                    if (trimmedInput.toLowerCase().startsWith("@math")) {
                        String prompt = trimmedInput.substring(8).trim();
                        logMessage(userId, encrypt("You: (Math) " + prompt));
                        String aiResponse = callAI(prompt, "qwen2-math:1.5b");
                        sendMessage("AI-Math: " + aiResponse);
                        logMessage(userId, encrypt("AI-Math: " + aiResponse));
                    } else if (trimmedInput.toLowerCase().startsWith("@ai")) {
                        String prompt = trimmedInput.substring(3).trim();
                        logMessage(userId, encrypt("You: (AI) " + prompt));
                        String aiResponse = callAI(prompt, "deepseek-r1:1.5b");
                        sendMessage("AI: " + aiResponse);
                        logMessage(userId, encrypt("AI: " + aiResponse));
                    }
                    // Private message: messages starting with '@' followed by the target userId.
                    else if (trimmedInput.startsWith("@")) {
                        String[] parts = trimmedInput.split(" ", 2);
                        if (parts.length == 2) {
                            try {
                                int targetUserId = Integer.parseInt(parts[0].substring(1));
                                sendMessageTo(targetUserId, parts[1], userId);
                            } catch (NumberFormatException e) {
                                sendMessage("Invalid user ID format. Please use '@<userID> message'.");
                            }
                        } else {
                            sendMessage("Invalid command. Use '@<userID> message' for private messaging.");
                        }
                    }
                    // Otherwise, treat the input as a group chat message.
                    else {
                        broadcastMessage(trimmedInput, userId);
                        System.out.println("[Group][User " + userId + "]: " + trimmedInput);
                    }
                }
            } catch (IOException e) {
                System.out.println("User " + userId + " disconnected.");
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                synchronized(clients) {
                    clients.remove(userId);
                }
            }
        }

        // Reads the encrypted chat history, decrypts it, and sends it to the client.
        private void sendChatHistory() {
            String fileName = "ChatLogs/user_" + userId + ".txt";
            File chatFile = new File(fileName);
            if (chatFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(chatFile))) {
                    String line;
                    out.println("--- Chat History ---");
                    while ((line = reader.readLine()) != null) {
                        out.println(decrypt(line));
                    }
                    out.println("--------------------");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }
    }
}
