# Triton AI — Android + API backend

Triton AI is a simple online AI chat app. The Android app is Kotlin; a small Node.js backend calls the Gemini API so the Gemini key is **not stored inside the APK**.

## Project layout

- `android/` — Android app source and Gradle build files
- `backend/` — Node.js API proxy for Gemini
- `.github/workflows/build-apk.yml` — builds and uploads a signed Release APK

## 1. Create the GitHub repository

1. Create a new GitHub repository named `triton-ai`.
2. Upload/push the contents of this project to the repository root (the `.github` folder must also be at the root).
3. Open the repository's **Settings → Secrets and variables → Actions → New repository secret**.

Create these secrets for a stable release-signing key:
- `ANDROID_KEYSTORE_BASE64` — Base64 text of your `.jks` keystore
- `ANDROID_KEYSTORE_PASSWORD` — keystore password
- `ANDROID_KEY_ALIAS` — key alias
- `ANDROID_KEY_PASSWORD` — key password

Create a keystore once on a computer with Java installed:
```bash
keytool -genkeypair -v -keystore triton-release.jks -alias triton -keyalg RSA -keysize 2048 -validity 10000
```
Encode it as one Base64 string:
- Linux: `base64 -w 0 triton-release.jks`
- macOS: `base64 triton-release.jks | tr -d '\\n'`
- Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("triton-release.jks"))`

Store the output in `ANDROID_KEYSTORE_BASE64`. Keep the keystore and passwords private. If you do not configure these secrets, the workflow generates a temporary signing key so you can test-install the APK; future APKs signed with a different temporary key cannot update that install. For a long-term app, configure the four secrets.

## 2. Deploy the backend

The backend must be deployed to a public HTTPS host. Example using Render:

1. Create a Web Service from this GitHub repository.
2. Set **Root Directory** to `backend`.
3. Runtime: Node.
4. Build Command: `npm install`
5. Start Command: `npm start`
6. Add environment variables:
   - `GEMINI_API_KEY` = your Gemini API key
   - `GEMINI_MODEL` = `gemini-2.5-flash` (change if this model is unavailable to your account)
   - `ALLOWED_ORIGIN` = `*`
7. Deploy and copy the HTTPS service URL, e.g. `https://your-service.onrender.com`.

Get a Gemini API key from Google AI Studio. Do not put it in the Android source, GitHub commits, or the APK.

## 3. Set the Android app's backend URL

Edit:
`android/app/src/main/java/com/tritonai/app/MainActivity.kt`

Change:
```kotlin
private val apiBaseUrl = "https://YOUR-BACKEND-URL.onrender.com"
```
to your actual backend URL, without a trailing slash. Commit the change. The GitHub Actions build will start again.

## 4. Build and download the APK

1. Open the repository's **Actions** tab.
2. Select **Build Triton AI Release APK**.
3. Click **Run workflow** (or push a commit to `main`).
4. Wait until the workflow is green.
5. Open the completed run and download the `triton-ai-release-apk` artifact.
6. Extract the ZIP and install `app-release.apk` on your Android phone. Android may ask you to allow installation from that file manager/browser.

## 5. Test backend locally (optional)

```bash
cd backend
npm install
# Set GEMINI_API_KEY in your shell environment before starting:
npm start
```

The backend listens on port 3000 by default. Test:
```bash
curl http://localhost:3000/health
```

## Important notes

- This is a starter app, not a production-hardened service. Add rate limiting, authentication, abuse monitoring, and usage limits before sharing the backend publicly.
- A public endpoint with `ALLOWED_ORIGIN=*` can be called by other clients too; CORS is not API security. Never expose your Gemini key.
- AI API availability, quotas, model names, and pricing depend on the provider/account and may change.
- GitHub Actions artifacts are downloadable build outputs, not a Google Play Store release.
