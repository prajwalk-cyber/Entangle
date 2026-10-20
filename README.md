# 🔐 Entangle — Post-Quantum Hybrid Encrypted Messenger for Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Platform-Android_14+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Cryptography](https://img.shields.io/badge/Crypto-Hybrid_PQC_%2B_AES--256--GCM-critical?style=for-the-badge)](https://csrc.nist.gov/)

> **Entangle** is an experimental, quantum-resistant secure messaging client for Android built with **Jetpack Compose**, **X25519 Elliptic Curve Diffie-Hellman (ECDH)**, **AES-256-GCM**, and a **Post-Quantum Hybrid Key Derivation Protocol** designed to safeguard real-time communications against *Harvest-Now, Decrypt-Later (HNDL)* quantum attacks.

---

## ⚡ Core Cryptographic Architecture

Entangle implements a defense-in-depth **Hybrid Cryptographic Scheme** combining proven classical elliptic-curve security with post-quantum key encapsulation readiness:

```
[ User A (Alice) ]                                               [ User B (Bob) ]
        |                                                                |
        | 1. Generate Ephemeral Keys:                                    |
        |    • Classical: X25519 KeyPair                                 |
        |    • Post-Quantum: Ephemeral Hint                              |
        |                                                                |
        | 2. Perform Key Agreement (XDH):                                |
        |    Classical Secret  = X25519(Alice_Priv, Bob_Pub)             |
        |    PQ Secret         = KDF(PQ_Private)                         |
        |                                                                |
        | 3. Hybrid Session Key Derivation:                              |
        |    Session Key       = SHA-256(Classical_Secret || PQ_Secret)  |
        |                                                                |
        | 4. Authenticated Payload Encryption:                           |
        |    Ciphertext, Nonce = AES-256-GCM(Message, Session Key)       |
        |                                                                |
        | ------------------- Encrypted Envelope ----------------------> |
        |        (Base64 Ciphertext + 96-bit Nonce + GCM Auth Tag)       |
        |                                                                |
        |                                                5. Decrypt:     |
        |                                                   AES-256-GCM  |
```

### Security Properties
- **Post-Quantum Hybrid Readiness:** Even if quantum computers solve the discrete logarithm problem for elliptic curves in the future, communications remain secure through hybrid combinatorial secrets.
- **Forward Secrecy:** Ephemeral key pairs generate fresh symmetric session keys for every communication session.
- **Authenticated Encryption:** Uses **AES-256-GCM** with 128-bit authentication tags to prevent replay, tampering, and bit-flipping attacks.
- **No Nonce Reuse:** Every individual message generates a secure cryptographically random 12-byte (96-bit) initialization vector (IV/Nonce).

---

## 🛠️ Tech Stack & Implementation

| Layer | Technologies |
| :--- | :--- |
| **Language** | Kotlin 2.0+ (100% Coroutines & Flow) |
| **UI Framework** | Jetpack Compose & Material Design 3 |
| **Cryptography** | Java Cryptography Architecture (JCA), X25519 Key Agreement (`XDH`), `AES/GCM/NoPadding`, `SHA-256` |
| **Architecture** | MVVM (Model-View-ViewModel), Repository Pattern, Clean Architecture |
| **Signaling & Relay**| Firebase Cloud Firestore (End-to-end encrypted payload routing) |

---

## 📁 Project Structure

```
app/src/main/java/com/example/entangle/
├── crypto/
│   ├── HybridCrypto.kt        # Core engine: X25519, AES-256-GCM & hybrid key agreement
│   └── KeyStorage.kt          # Secure on-device key management
├── model/
│   ├── Message.kt             # Encrypted message payload structure
│   ├── Session.kt             # Cryptographic handshake session state
│   └── User.kt                # User cryptographic identity & public keys
├── repository/
│   └── FirebaseRepository.kt  # E2EE ciphertext dispatch and Firestore sync
├── ui/
│   ├── screen/                # Modern Compose screens (Chat, Setup, User Directory)
│   └── theme/                 # Dark/Light cybersecurity theme palette
└── viewmodel/
    ├── ChatViewModel.kt       # Real-time encryption/decryption state orchestration
    ├── SetupViewModel.kt      # Identity key generation & registration
    └── UserListViewModel.kt   # Dynamic peer discovery
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug / Iguana or newer
- JDK 17+
- Android SDK 34 (Android 14) or higher

### Build & Run
1. Clone the repository:
   ```bash
   git clone git@github.com:prajwalk-cyber/Entangle.git
   cd Entangle
   ```
2. Open the project in Android Studio.
3. Configure your Firebase project and add your `google-services.json` to `/app`.
4. Build and deploy to an emulator or physical device:
   ```bash
   ./gradlew assembleDebug
   ```

---

## ⚠️ Security Notice & Roadmap
- **Production ML-KEM Migration:** Entangle currently runs simulated PQ hints for protocol benchmarking. The next phase introduces direct bindings to NIST FIPS 203 (ML-KEM-768 / Kyber) via Bouncy Castle PQC.
- Android Keystore integration for hardware-backed master identity keys (StrongBox Keymaster).

---

## 📜 License
This project is open-source under the MIT License.
