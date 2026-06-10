**The assignment:**
"Create a applet that uses javaFX, regex patterns, ".

1) I brainstormed ideas and decided to create a P2P (Peer-To-Peer) encrypted messenger, meaning two users communicate directly with no server in between and every message is scrambled before it leaves the sender's machine.
2) I downloaded the JavaFX SDK and set up Visual Studio Code to work with JavaFX by configuring the module path in the run settings.
3) I planned the application layout on paper: a setup section (name, port, peer IP, password), a decrypted chat area, an encrypted traffic area that shows the raw packets, and a message input row at the bottom.
4) I built the UI in JavaFX using `GridPane` for the setup fields, `VBox` as the root container, `HBox` rows for buttons and the message input, and two `TextArea` boxes for the chat and encrypted traffic areas.
5) I added three connection buttons: "Enter As Peer" (opens a server socket and waits for the other user to connect), "Connect To Peer" (connects directly to the peer's IP and port), and "Terminate Connection" (closes the socket and resets the UI).
6) I implemented input validation using `Pattern` (Regex) to make sure names are 3–15 letters, ports are in the valid 1024–65535 range, IP addresses follow the correct format, and messages only contain allowed characters.
7) I implemented PBKDF2 (`PBEKeySpec` + `SecretKeyFactory`) to convert the shared password into a 128-bit AES key using HMAC-SHA256 over 65,536 rounds with a fixed salt — this way both users derive the exact same key from the same password without ever sending the key over the network.
8) I implemented AES/GCM encryption: before sending, a random 12-byte IV is generated with `SecureRandom`, the message is encrypted with `AES/GCM/NoPadding` using a 128-bit authentication tag, and the result is sent as `Base64(IV):Base64(Ciphertext)` over the socket.
9) I implemented decryption on the receiver side: the packet is split at `:` to recover the IV and ciphertext, both are decoded from Base64, and `AES/GCM` decrypts while automatically verifying the authentication tag — if the data was tampered with it throws an error and shows a warning instead of displaying the message.
10) I used `Platform.runLater()` everywhere that background network threads needed to update the UI, since JavaFX only allows UI changes from the JavaFX Application Thread.
11) I created a `style.css` file and loaded it into the scene to style all controls (dark background, red borders, custom button and alert colours) keeping the visual design consistent.
12) I ran both sides of the application on my own machine (using `localhost` and two separate terminal windows) to confirm the encryption, decryption, and tamper detection all worked correctly.
13) I documented my project and created a project poster (see link below).

<a href="JavaFX%20Project/JavaFX%20Projectfin.html" target="_blank"> Project's Poster</a>

**Here Are The Code Files:**

[App.java](JavaFX%20Project/App.java)

[style.css](JavaFX%20Project/style.css)


**How The Application Works Step By Step:**
1. User1 enters a name, a port number and a shared password, then clicks "Enter As Peer". The code opens a server socket on that port and waits.
2. User2 enters a name, the same password and User1's IP address, then clicks "Connect To Peer". The code connects directly using a TCP socket with no server involved.
3. PBKDF2 converts the shared password into a 128-bit AES key on both sides. Because the same password, salt, and iteration count are used, both keys are identical and neither one is ever sent over the network.
4. When User1 types a message and clicks "Send Message", the code generates a random 12-byte IV, encrypts the message with AES/GCM, and sends the packet as `Base64(IV):Base64(Ciphertext)` over the socket.
5. User2's code reads the packet, splits it at `:`, decodes both parts from Base64, and decrypts with AES/GCM. The 128-bit authentication tag is verified automatically — if the message was altered in transit it is rejected and a warning is shown.
6. The decrypted message appears in the chat area and the raw encrypted packet is shown in the encrypted traffic area so both users can see exactly what travels over the network.


**How To Run The Application:**
1. Download and unzip the JavaFX SDK from https://gluonhq.com/products/javafx/.
2. Open the project folder in Visual Studio Code and install the "Extension Pack for Java".
3. In `.vscode/settings.json` add the JavaFX `lib` folder to `java.project.referencedLibraries`.
4. In the run configuration (`.vscode/launch.json`) add `--module-path <path-to-javafx-lib> --add-modules javafx.controls,javafx.fxml` to `vmArgs`.
5. Open `App.java` and press the Run button.
6. Run a second instance of the application to act as the second peer.
7. On the first instance click "Enter As Peer", on the second instance enter the first instance's IP and click "Connect To Peer".
8. Both sides must enter the same password — messages will only decrypt correctly if the passwords match.


**What I Learned From This Project:**
1. How to set up and use the JavaFX SDK in Visual Studio Code.
2. How to design multi-panel JavaFX layouts using VBox, HBox and GridPane.
3. How P2P TCP socket connections work (ServerSocket, Socket, BufferedReader, PrintWriter).
4. How to use Platform.runLater() to safely update the JavaFX UI from background threads.
5. How PBKDF2 derives a strong cryptographic key from a plain-text password.
6. How AES/GCM encryption and decryption works including IVs and authentication tags.
7. How to use SecureRandom for generating cryptographically safe IVs.
8. How Base64 encoding is used to safely transmit binary data as text over a socket.
9. How to validate user input with Regex patterns in Java.
10. How to apply CSS styling to JavaFX scenes and alert dialogs.


**Notes and Etceteras:**
1. Both users must enter the exact same password — even one character difference will produce a different key and decryption will fail.
2. The port number must be between 1024 and 65535. Ports below 1024 are reserved by the system.
3. The "Enter As Peer" user must start listening before the other user clicks "Connect To Peer".
4. Make sure the port you use is not blocked by a firewall.
5. When testing on the same machine use `127.0.0.1` (localhost) as the peer IP.
6. The application only supports one-to-one connections — you cannot connect more than two peers at once.
7. If a message fails to decrypt a warning is shown instead of displaying garbage text, which is intentional tamper detection.
8. The fixed salt in PBKDF2 means security relies entirely on a strong, secret shared password — use a long password that only the two users know.
9. Closing the window automatically terminates the connection cleanly via the `setOnCloseRequest` handler.
