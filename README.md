# sheets-export-poc

POC Spring Boot (Java 17) : une **page web avec un tableau et un bouton « Exporter au format
Google Sheet »**. Un clic génère un nouveau Google Sheet **dans le Drive de l'utilisateur** et
l'ouvre dans un nouvel onglet.

Le Sheet contient :

- un onglet **`Data`** (visible) : les lignes du tableau + une colonne avec un **menu déroulant** (data validation) ;
- un onglet **`RefData`** (`hidden: true`) : la liste de valeurs source du menu déroulant (`ONE_OF_RANGE`) ;
- partage **optionnel** avec un email tiers (role `writer`).

Un nouveau fichier est créé **à chaque clic**. **Aucune base de données**, aucun spreadsheet ID persisté.

---

## Expérience utilisateur

1. L'utilisateur ouvre `http://localhost:8080/` → tableau + bouton.
2. Clic sur **« Exporter au format Google Sheet »**.
3. **Première fois seulement** : écran de consentement Google (« Se connecter avec Google » +
   autorisation Sheets/Drive). L'export se relance ensuite automatiquement.
4. Le Google Sheet s'ouvre dans un nouvel onglet, prêt à l'emploi.

Le **serveur** gère tout le flow OAuth (Spring Security). Le token d'accès Google vit dans la
**session HTTP** (en mémoire) — le front ne le manipule jamais, il n'y a aucun `curl` ni copier-coller de token.

---

## 1. Prérequis Google Cloud

### 1.1 Projet + APIs

1. <https://console.cloud.google.com/> → créer/choisir un projet.
2. Activer :
   - **Google Sheets API** : <https://console.cloud.google.com/apis/library/sheets.googleapis.com>
   - **Google Drive API** : <https://console.cloud.google.com/apis/library/drive.googleapis.com>

### 1.2 Écran de consentement OAuth

**APIs & Services → OAuth consent screen** :

- User type : **External** (ou Internal si Workspace).
- Champs obligatoires renseignés.
- **Scopes** : ajouter `.../auth/spreadsheets` et `.../auth/drive` (+ `openid`, `email`, `profile`).
- En mode *Testing* : ajouter votre adresse dans **Test users** (sinon `access_denied`).

### 1.3 Client OAuth « Web application »

**APIs & Services → Credentials → Create credentials → OAuth client ID** :

- Application type : **Web application**
- **Authorized redirect URIs** :
  ```
  http://localhost:8080/login/oauth2/code/google
  ```
- Récupérer le **Client ID** et le **Client secret**.

---

## 2. Configuration

`cp .env.example .env` puis renseigner :

| Variable | Rôle |
|---|---|
| `GOOGLE_OAUTH_CLIENT_ID` | Client ID OAuth (obligatoire) |
| `GOOGLE_OAUTH_CLIENT_SECRET` | Client secret OAuth (obligatoire) |
| `GOOGLE_APPLICATION_NAME` | *(optionnel)* nom transmis aux APIs Google, défaut `sheets-export-poc` |
| `SHEETS_EXPORT_MAX_RETRIES` / `SHEETS_EXPORT_INITIAL_BACKOFF_MS` | *(optionnel)* retry sur 429/500/503 |

L'app **ne démarre pas** sans `GOOGLE_OAUTH_CLIENT_ID` / `_SECRET`.

---

## 3. Lancer

### 3.1 Docker (recommandé)

```bash
cp .env.example .env    # + renseigner CLIENT_ID / CLIENT_SECRET
docker compose up --build
```

> **Erreur `docker-credential-desktop: executable file not found` ?**
> Le helper de Docker Desktop n'est pas dans le `PATH`. Soit préfixer :
> `PATH="/Applications/Docker.app/Contents/Resources/bin:$PATH" docker compose up --build`,
> soit ajouter ce dossier au `PATH` dans `~/.zshrc`, soit retirer la ligne
> `"credsStore": "desktop"` de `~/.docker/config.json` (bloc `auths` vide).

### 3.2 En local (Maven)

Prérequis : **JDK 17+**, **Maven 3.9+**.

```bash
export GOOGLE_OAUTH_CLIENT_ID=...
export GOOGLE_OAUTH_CLIENT_SECRET=...
mvn spring-boot:run
```

Puis ouvrir **<http://localhost:8080/>**.

---

## 4. Utiliser

Ouvrir <http://localhost:8080/>, cliquer **« Exporter au format Google Sheet »**.

Le tableau de la page est un exemple d'inventaire (colonnes *Produit / Quantité / Statut*).
Le menu déroulant est posé sur la colonne **Statut** (index `2`), valeurs
`À commander / En stock / Rupture`.

Pour partager le fichier avec un tiers, ajouter `shareWithEmail` au payload construit par le
front (`src/main/resources/static/index.html`).

### API sous-jacente

`POST /api/exports/sheet` — **exige une session authentifiée** (cookie ; sinon `401`).

```json
// body
{
  "sheetTitle": "Inventaire 28/08/2026",
  "shareWithEmail": "collegue@example.com",   // optionnel
  "rows": [
    { "Produit": "Café", "Quantité": "12", "Statut": "" }
  ],
  "dropdownOptions": ["À commander", "En stock", "Rupture"],
  "dropdownColumnIndex": 2
}
// réponse
{ "spreadsheetId": "1AbC...", "spreadsheetUrl": "https://docs.google.com/spreadsheets/d/1AbC.../edit" }
```

| Autres routes | |
|---|---|
| `GET /` | page HTML (tableau + bouton) |
| `GET /api/session` | `{ "authenticated": bool, "email": string\|null }` |
| `GET /oauth2/authorization/google` | démarre le consentement Google (géré par Spring Security) |
| `POST /logout` | ferme la session |

### Codes d'erreur

| HTTP | Quand |
|---|---|
| `400` | body invalide (titre manquant, email mal formé, `dropdownOptions` vide, index < 0) — détail par champ dans `details` |
| `401` | pas de session Google / session expirée → le front relance le consentement |
| `429` | quota Google dépassé même après retries |
| `502` | autre erreur renvoyée par l'API Google |

---

## 5. Structure

```
src/main/java/com/example/sheetsexport/
├── SheetsExportPocApplication.java
├── config/
│   ├── GoogleProperties.java          # google.application-name
│   ├── GoogleApiConfig.java           # beans HttpTransport + JsonFactory
│   └── SecurityConfig.java            # oauth2Login Google ; /api/exports/** protégé ; 401 sur /api/**
├── web/
│   ├── ExportController.java          # POST /api/exports/sheet — token via @RegisteredOAuth2AuthorizedClient
│   ├── SessionController.java         # GET /api/session
│   ├── GlobalExceptionHandler.java    # 400 / 401 / 429 / 502
│   └── dto/{SheetExportRequest,SheetExportResponse}.java
├── service/
│   ├── GoogleClientFactory.java       # clients Sheets/Drive à la volée depuis l'access token (header Bearer)
│   ├── GoogleSheetsExportService.java # create(2 onglets) → values.batchUpdate → setDataValidation → partage
│   └── RetryExecutor.java             # backoff exponentiel + jitter sur 429/500/503
└── exception/{InvalidAccessTokenException,QuotaExceededException,SheetsExportException}.java

src/main/resources/
├── static/index.html                 # la page : tableau + bouton + JS (fetch, relance après login)
└── application.yml

src/test/java/.../web/ExportControllerTest.java   # @SpringBootTest, service mocké, session OAuth2 simulée
```

### Enchaînement des appels Google (`GoogleSheetsExportService`)

1. `spreadsheets.create` — un seul appel définit les **deux** onglets : `Data` (sheetId 0, visible) et `RefData` (sheetId 1, `hidden: true`).
2. `spreadsheets.values.batchUpdate` — écrit les `rows` dans `Data!A1`, les `dropdownOptions` dans `RefData!A1`.
3. `spreadsheets.batchUpdate` + `setDataValidation` — règle `ONE_OF_RANGE` sur la colonne `dropdownColumnIndex` de `Data`, condition `=RefData!A1:A<n>`.
4. `drive.permissions.create` — **seulement si** `shareWithEmail` fourni (`type: user`, `role: writer`).

---

## 6. Notes / limites (POC)

- **Accès `online`** : pas de refresh token stocké. Si le token expire (~1h) pendant que l'onglet est ouvert, le prochain clic repasse (en général sans ré-afficher l'écran de consentement) par Google puis relance l'export.
- Session en mémoire → une seule instance. En cluster : sessions collantes ou store partagé (Redis…).
- Écran de consentement à *publier* (ou utilisateur en *test user*) sinon `access_denied`.
- CSRF désactivé sur `/api/**` (POC, appels `fetch` même origine, auth par cookie).
- Versions des libs Google figées dans `pom.xml` — bump possible.
