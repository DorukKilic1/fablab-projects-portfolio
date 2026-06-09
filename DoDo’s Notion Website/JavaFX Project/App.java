import javafx.application.Application;  // Application Class.
import javafx.application.Platform; //  Makes Background Network Threads Able To Update UI Safely.
import javafx.geometry.Insets;  // Padding For Layout.
import javafx.scene.Scene;  // Scene: Holds All FX Controls.
import javafx.scene.control.*;  // Controls (Label,Button,TextField,TextArea,PasswordField,Alert,etc).
import javafx.scene.layout.*;   // Layout Containers (VBox, HBox, GridPane etc).
import javafx.stage.Stage;  // Main FX Window.
import javax.crypto.Cipher; // For AES Encryption/Decryption.
import javax.crypto.SecretKey;  // For Storing AES Encryption Key.
import javax.crypto.SecretKeyFactory;   // Creating Key From Password.
import javax.crypto.spec.GCMParameterSpec;  // For AES/GCM Encryption Settings.
import javax.crypto.spec.PBEKeySpec;    // For Password Based Key Making.
import javax.crypto.spec.SecretKeySpec; // For Converting Key Bytes To AES Key Obj.
import java.io.*;   // Basic Java Import.
import java.net.*;  // Server/Socket Makes Direct P2P Communication.
import java.nio.charset.StandardCharsets;   // Makes All Texts Use UTF-8 Encoding.
import java.security.SecureRandom;  // Secure IV Making.
import java.util.Base64;    // Makes Encrypted Binary Data Text.
import java.util.regex.Pattern; // Pattern For Regex Validation.
import java.time.LocalDateTime; // For Getting Current Date And Time.
import java.time.format.DateTimeFormatter; // For Formatting The Date And Time.

public class App extends Application
{
    private TextArea chatArea;
    private TextArea encryptedArea;
    private TextField nameField;
    private TextField portField;
    private TextField peerIpField;
    private PasswordField passwordField;
    private TextField messageField;
    private Label statusLabel;
    private ServerSocket serverSocket;
    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private SecretKey aesKey;
    private volatile boolean running;
    private final SecureRandom random = new SecureRandom();
    private final Pattern nameRegex = Pattern.compile("^[a-zA-Z ]{3,15}$");
    private final Pattern ipRegex = Pattern.compile("^((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])$");
    private final Pattern messageRegex = Pattern.compile("^[a-zA-Z0-9\\s.,!?@#%&()_\\-':;]{1,250}$");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"); // Reusable Time Formatter.

    @Override
    public void start(Stage stage)
    {
        stage.setTitle("Java FX Project");
        nameField = new TextField();
        portField = new TextField();
        peerIpField = new TextField();
        passwordField = new PasswordField();

        Button buttonPeer = new Button("Enter As Peer");
        Button buttonBase = new Button("Connect To Peer");
        Button buttonStop = new Button("Terminate Connection");
        buttonStop.setVisible(false);
        buttonStop.setManaged(false);
        Button buttonSend = new Button("Send Message");

        buttonPeer.setOnAction(event -> listenAsPeer(buttonPeer, buttonBase, buttonStop));
        buttonBase.setOnAction(event -> connectToPeer(buttonPeer, buttonBase, buttonStop));
        buttonStop.setOnAction(event ->
        {
            stopConnection();
            showSomeButton(buttonPeer, buttonBase, buttonStop, true);
            lockFields(false);
        });
        buttonSend.setOnAction(event -> sendMessage());

        GridPane setupGrid = new GridPane();
        setupGrid.setHgap(10);
        setupGrid.setVgap(20);
        setupGrid.add(new Label("Name:"), 0, 0);
        setupGrid.add(nameField, 1, 0);
        setupGrid.add(new Label("Port:"), 0, 1);
        setupGrid.add(portField, 1, 1);
        setupGrid.add(new Label("Peer IP:"), 0, 2);
        setupGrid.add(peerIpField, 1, 2);
        setupGrid.add(new Label("Password:"), 0, 3);
        setupGrid.add(passwordField, 1, 3);

        HBox connectionButtons = new HBox(10, buttonPeer, buttonBase, buttonStop);
        buttonPeer.setMaxWidth(Double.MAX_VALUE);
        buttonBase.setMaxWidth(Double.MAX_VALUE);
        buttonStop.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(buttonPeer, Priority.ALWAYS);
        HBox.setHgrow(buttonBase, Priority.ALWAYS);
        HBox.setHgrow(buttonStop, Priority.ALWAYS);
        setupGrid.add(connectionButtons, 1, 4);

        ColumnConstraints leftColumn = new ColumnConstraints();
        leftColumn.setMinWidth(140);
        ColumnConstraints rightColumn = new ColumnConstraints();
        rightColumn.setHgrow(Priority.ALWAYS);
        setupGrid.getColumnConstraints().addAll(leftColumn, rightColumn);

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setPromptText("Decrypted Messages Area.");
        chatArea.setPrefHeight(260);

        encryptedArea = new TextArea();
        encryptedArea.setEditable(false);
        encryptedArea.setPromptText("Encrypted Messages Area.");
        encryptedArea.setPrefHeight(140);

        messageField = new TextField();
        messageField.setOnAction(event -> sendMessage());

        HBox messageBox = new HBox(10, messageField, buttonSend);
        HBox.setHgrow(messageField, Priority.ALWAYS);

        statusLabel = new Label("Status: Not connected.");

        VBox root = new VBox(12, setupGrid, new Label("Chat:"), chatArea, new Label("Encrypted Network Traffic:"), encryptedArea, messageBox, statusLabel);
        root.setPadding(new Insets(15));

        setupGrid.setId("setupGrid"); // Lets The CSS File Target This Grid By ID.

        Scene scene = new Scene(root, 800, 800);
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm()); // Loads All Styles From The CSS File.
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> stopConnection());
        stage.show();
    }
    private void listenAsPeer(Button buttonPeer, Button buttonBase, Button buttonStop)
    {
    if (!prepareConnection(buttonPeer, buttonBase, buttonStop, true)) return;
        try
        {   // Converts the local port text into an integer.
            int port = Integer.parseInt(portField.getText().trim());
            // Creates a server socket only for accepting the direct peer connection.
            serverSocket = new ServerSocket(port);
            // Updates the status label.
            setStatus("Listening As Peer on Port: " + port + ".");
            new Thread(() ->
            {   // Tries to accept one direct peer connection then reads messages in the same thread.
                try
                {   // Waits until another peer connects directly.
                    socket = serverSocket.accept();
                    // Sets up input and output streams for the direct peer socket.
                    setupStreams();
                    // Updates the chat area with the connected peer address.
                    appendTo(chatArea, "System: Peer Connected From " + socket.getInetAddress().getHostAddress());
                    setStatus("Connection Established.");
                    readMessages(buttonPeer, buttonBase, buttonStop);
                // Catches errors that happen while listening.
                }
                catch (IOException ex)
                {   // Shows an error only if the user did not intentionally stop the applet.
                    if (running)
                    {
                        running = false;
                        showError("Listening Failed: " + ex.getMessage());
                        setStatus("Listening Failed.");
                        resetUI(buttonPeer, buttonBase, buttonStop);
                    }
                }
            // Names and starts the listening thread.
            }, "Listen Thread").start();
        // Catches errors that happen while opening the listening port.
        }
        catch (Exception ex)
        {
            running = false;
            showError("Could Not Start Listening: " + ex.getMessage());
            setStatus("Listening Failed.");
            showSomeButton(buttonPeer, buttonBase, buttonStop, true);
            lockFields(false);
        }
    }
     // Connects this peer directly to another listening peer.
    private void connectToPeer(Button buttonPeer, Button buttonBase, Button buttonStop)
    {   // Stops if the name, local port, or password is invalid.
        if (!prepareConnection(buttonPeer, buttonBase, buttonStop, false)) return;
        try
        {
            String peerIp = peerIpField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            setStatus("Connecting To Peer...");
            // Creates a background thread so the screen does not freeze while connecting.
            new Thread(() ->
            {
                try
                {
                    socket = new Socket(peerIp, port);
                    // Sets up input and output streams for the direct peer socket.
                    setupStreams();
                    // Updates the chat area with the connected peer address.
                    appendTo(chatArea, "System: Connected To Peer " + peerIp + ":" + port);
                    // Updates the status label after connection.
                    setStatus("Connection Established.");
                    // Starts reading encrypted messages from the connected peer.
                    readMessages(buttonPeer, buttonBase, buttonStop);
                // Catches connection errors.
                }
                catch (IOException ex)
                {
                    running = false;
                    showError("Connection Failed: " + ex.getMessage());
                    setStatus("Connection Failed.");
                    resetUI(buttonPeer, buttonBase, buttonStop);
                }
            }, "Connect Thread").start();
        // Catches key creation or setup errors.
        }
        catch (Exception ex)
        {// Shows a connection setup error.
            running = false;
            showError("Could Not Connect: " + ex.getMessage());
            showSomeButton(buttonPeer, buttonBase, buttonStop, true);
            lockFields(false);
        }
    }
    // Handles validation, key creation, and UI locking shared by both connection modes.
    private boolean prepareConnection(Button buttonPeer, Button buttonBase, Button buttonStop, boolean isHost)
    {
        if (!validateFields(isHost)) return false;
        try
        {   // Creates the AES key from the shared password.
            aesKey = createKeyFromPassword(passwordField.getText());
        }
        catch (Exception ex)
        {
            showError("Key Error: " + ex.getMessage());
            return false;
        } // Marks the applet as running.
        running = true;
        showSomeButton(buttonPeer, buttonBase, buttonStop, false);
        lockFields(true);
        return true;
    }
    // Creates the text input and output streams for the direct socket.
    private void setupStreams() throws IOException
    {   // Creates a UTF-8 reader for encrypted incoming packets.
        input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        // Creates a UTF-8 writer for encrypted outgoing packets.
        output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }
    // Reads encrypted messages in a loop directly on the calling thread.
    private void readMessages(Button buttonPeer, Button buttonBase, Button buttonStop)
    {
        try
        {   // Stores each received encrypted packet.
            String packet;
            // Reads one encrypted packet per line.
            while (running && (packet = input.readLine()) != null)
            {   // Shows the encrypted packet in the encrypted traffic area.
                appendTo(encryptedArea, "Received Encrypted Packet:\n" + packet + "\n");
                // Tries to decrypt the encrypted packet.
                try
                {   // Decrypts the packet into readable text.
                    String decryptedMessage = decryptPacket(packet);
                    appendTo(chatArea, decryptedMessage); // Shows the decrypted message in the chat area.
                } // Catches decryption or tampering errors.
                catch (Exception ex)
                {   // Shows a warning if the message cannot be decrypted.
                    appendTo(chatArea, "Warning: Message Couldn't Be Decrypted.");
                }
            }
        } // Catches socket read errors.
        catch (IOException ex)
        {   // Shows a disconnect message only if the applet was running.
            if (running) appendTo(chatArea, "System: Peer Disconnected."); // Always runs when reading stops.
        }
        finally
        {   // Updates the status label after the reading loop ends.
            running = false;
            setStatus("Not Connected.");
            output = null;
            input = null;
            resetUI(buttonPeer, buttonBase, buttonStop);
        }
    }
    // Encrypts and sends one message to the connected peer.
    private void sendMessage()
    {
    String name = nameField.getText().trim(); // Name Input.
    String message = messageField.getText().trim(); // Message Input.
    if (output == null)
    {   // Shows an error if there isnt any peer to send to.
        showError("No Peer Connected.");
        return;
    }
    else if (!nameRegex.matcher(name).matches() || !messageRegex.matcher(message).matches())
    {   // Shows an error if the name or message is invalid.
        showError("Empty Message.");
        return;
    }  // Tries to encrypt and send the message.
        try
        {
            String time = LocalDateTime.now().format(TIME_FMT); //saves the date and time of the message.
            String encryptedPacket = encryptMessage("[" + time + "] " + name + ": " + message); // Encrypts the message.
            output.println(encryptedPacket); // Sends encrypted packet to peer.
            appendTo(chatArea, "[" + time + "] Me: " + message); // Shows message at chat area.
            appendTo(encryptedArea, "Sent Encrypted Packet:\n" + encryptedPacket + "\n"); // Shows the encrypted packet in the encrypted traffic area.
            messageField.clear();   // Clears the message input field.
        } // Catches encryption or sending errors.
        catch (Exception ex)
        {   // Shows an encryption error.
            showError("Couldn't Encrypt Message: " + ex.getMessage());
        }
    }
    private boolean validateFields(boolean isHost)
    {
    try
    {
        int port = Integer.parseInt(portField.getText().trim());
        if (port < 1024 || port > 65535) throw new NumberFormatException();
    }
    catch (NumberFormatException ex)
    {
        showError("Invalid Port.");
        return false;
    }
    if (!nameRegex.matcher(nameField.getText().trim()).matches() || passwordField.getText().length() < 8 || (!isHost && !ipRegex.matcher(peerIpField.getText().trim()).matches()))
    {
        showError("Invalid Info.");
        return false;
    }
    return true;
    }
    // Creates an AES key from the shared password using PBKDF2.
    private SecretKey createKeyFromPassword(String pass) throws Exception
    {   // Creates a fixed salt so both peers generate the same key from the same password.
        byte[] salt = "Mr.DoDo".getBytes(StandardCharsets.UTF_8);
        // Creates the password-based key specification.
        PBEKeySpec spec = new PBEKeySpec(pass.toCharArray(), salt, 65536, 128);
        // Creates a PBKDF2 key factory using HMAC-SHA256.
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        // Generates the raw AES key bytes.
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        // Converts the raw key bytes into an AES SecretKey.
        return new SecretKeySpec(keyBytes, "AES");
    }
    // Encrypts readable text using AES/GCM.
    private String encryptMessage(String plainText) throws Exception
    {   // Creates a 12-byte IV for AES/GCM.
        byte[] iv = new byte[12];
        // Fills the IV with secure random bytes.
        random.nextBytes(iv);
        // Creates the AES/GCM cipher.
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        // Creates GCM settings with a 128-bit authentication tag.
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        // Initializes the cipher in encryption mode.
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec);
        // Encrypts the plain text into encrypted bytes.
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        // Converts the IV into Base64 text.
        String ivText = Base64.getEncoder().encodeToString(iv);
        // Converts the encrypted bytes into Base64 text.
        String cipherText = Base64.getEncoder().encodeToString(encryptedBytes);
        // Returns the full encrypted packet. Integrity is guaranteed by the GCM auth tag inside cipherText.
        return ivText + ":" + cipherText;
    }
    // Decrypts an encrypted packet using AES/GCM.
    private String decryptPacket(String packet) throws Exception
    {   // Splits the packet into IV and ciphertext.
        String[] parts = packet.split(":", 2);
        // Checks whether the packet has exactly two parts.
        if (parts.length != 2)
        {   // Throws an error if the packet format is wrong.
            throw new SecurityException("Invalid Packet Format.");
        }
        // Converts the Base64 IV text back into bytes.
        byte[] iv = Base64.getDecoder().decode(parts[0]);
        // Converts the Base64 ciphertext text back into encrypted bytes.
        byte[] encryptedBytes = Base64.getDecoder().decode(parts[1]);
        // Creates the AES/GCM cipher.
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        // Creates GCM settings using the received IV.
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        // Initializes the cipher in decryption mode.
        cipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec);
        // Decrypts and verifies integrity via the GCM auth tag. Throws AEADBadTagException if tampered.
        byte[] plainBytes = cipher.doFinal(encryptedBytes);
        // Converts the plain text bytes into a readable string.
        return new String(plainBytes, StandardCharsets.UTF_8);
    }
    // Stops the direct peer connection and closes sockets.
    private void stopConnection()
    {   // Marks the applet as no longer running.
        running = false;
        closeQuietly(socket);       // Tries to close the direct peer socket.
        closeQuietly(serverSocket); // Tries to close the listening socket.
        output = null;  // Removes the output writer reference.
        input = null;   // Removes the input reader reference.
        socket = null;
        serverSocket = null;
        setStatus("Stopped.");  // Updates the status label.
    }
    // Resets connection buttons and unlocks fields safely from any thread.
    private void resetUI(Button buttonPeer, Button buttonBase, Button buttonStop)
    {
        Platform.runLater(() -> { showSomeButton(buttonPeer, buttonBase, buttonStop, true); lockFields(false); });
    }
    // Closes any Closeable silently, ignoring errors because the applet is stopping.
    private void closeQuietly(Closeable c)
    {
        try 
        { 
            if (c != null) c.close(); 
        } 
        catch (IOException ignored) 
        {}
    }
    // Adds text to a TextArea safely from any thread.
    private void appendTo(TextArea area, String text)
    {   // Runs the UI update on the JavaFX application thread.
        Platform.runLater(() -> area.appendText(text + "\n"));
    }
    // Updates the status label safely.
    private void setStatus(String text)
    {   // Runs the UI update on the JavaFX application thread.
        Platform.runLater(() -> statusLabel.setText("Status: " + text));
    }
    // Shows an error popup safely.
    private void showError(String text)
    {   // Runs the alert on the JavaFX application thread.
        Platform.runLater(() ->
        {   // Creates an error alert.
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");    // Sets Title of Error.
            alert.setHeaderText(null);  // Removes default alert header.
            alert.setContentText(text); // Sets error message.
            alert.getDialogPane().getStylesheets().add(getClass().getResource("style.css").toExternalForm()); // Applies The Same CSS To The Alert.
            alert.showAndWait();    // Shows the alert and waits for the user to close it.
        });
    }
    private void showSomeButton(Button buttonPeer, Button buttonBase, Button buttonStop, boolean showButton) //shows or hides the buttons.
    {
        setButtonVisible(buttonPeer, showButton);
        setButtonVisible(buttonBase, showButton);
        setButtonVisible(buttonStop, !showButton);
    }
    private void setButtonVisible(Button button, boolean visible) // enables & disables which buttons are shown.
    {
        button.setVisible(visible);
        button.setManaged(visible);
    }
    private void lockFields(boolean locked) //locks & unlocks the text areas.
    {
        nameField.setEditable(!locked);
        portField.setEditable(!locked);
        peerIpField.setEditable(!locked);
        passwordField.setEditable(!locked);
    }
    public static void main(String[] args)
    {
        launch(args);
    }
}
