# MixerBuyParts — App Android

Progetto Android (WebView) per l'applicazione MixerBuyParts.

---

## Requisiti

| Strumento | Versione minima |
|-----------|----------------|
| Android Studio | Hedgehog (2023.1) o superiore |
| JDK | 17 (incluso con Android Studio) |
| Android SDK | API 34 (installabile da SDK Manager) |
| Dispositivo/Emulatore | Android 7.0+ (API 24) |

---

## Struttura del progetto

```
MixerBuyParts/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   └── index.html          ← L'intera app HTML
│   │   ├── java/com/mixerbuyparts/app/
│   │   │   └── MainActivity.java   ← Activity principale
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   ├── values/strings.xml
│   │   │   ├── values/themes.xml
│   │   │   ├── values/colors.xml
│   │   │   ├── xml/file_paths.xml
│   │   │   └── mipmap-*/ic_launcher*.png
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## Come compilare e installare

### 1. Apri il progetto in Android Studio
- `File → Open` → seleziona la cartella `MixerBuyParts`
- Aspetta che Gradle scarichi le dipendenze (~1-2 minuti)

### 2. Sync Gradle
- Se compare il banner "Gradle files have changed", clicca **Sync Now**

### 3. Collega il telefono (o avvia emulatore)
- **Telefono fisico**: abilita "Opzioni sviluppatore" → "Debug USB" nelle impostazioni Android
- **Emulatore**: `Tools → Device Manager → Create Device`

### 4. Esegui l'app
- Clicca il tasto ▶️ **Run** (o `Shift+F10`)
- Seleziona il dispositivo e conferma

---

## Funzionalità native implementate

| Funzione HTML | Soluzione Android |
|---------------|-------------------|
| `<input type="file">` CSV | File picker nativo tramite `WebChromeClient.onShowFileChooser()` |
| `window.print()` | Intercettato → Print dialog nativo Android |
| `jsPDF .save()` PDF | `AndroidBridge.saveBase64File()` → salva in Download/ |
| `downloadFile()` CSV | `AndroidBridge.saveBase64File()` → salva in Download/ |
| `localStorage` | Funziona nativamente in WebView (DOM Storage abilitato) |
| Font Google | Caricati da internet (richiede connessione al primo avvio) |
| CDN jsPDF / html2canvas | Caricati da internet |

---

## Nota sui download

Tutti i file esportati (PDF, CSV) vengono salvati in:
```
/storage/emulated/0/Download/
```
E sono accessibili dall'app **File Manager** del telefono o dalle notifiche.

---

## Problemi comuni

**"Gradle sync failed"**
→ Controlla la connessione internet (deve scaricare le dipendenze da Maven)

**L'app si apre ma la pagina è bianca**
→ Il file `index.html` non è in `app/src/main/assets/`. Verifica che sia presente.

**I font non si caricano**
→ Normale al primo avvio senza internet. Con connessione funziona normalmente.

**Il file picker non si apre**
→ Assicurati che il telefono abbia un'app file manager installata.

---

## Generare l'APK di release

```
Build → Generate Signed Bundle / APK → APK → Next
```
Crea un keystore (o usa uno esistente) e compila. L'APK sarà in:
```
app/release/app-release.apk
```
