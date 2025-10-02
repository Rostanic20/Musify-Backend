# OAuth2 with PKCE Implementation Guide

## Overview

This document describes the OAuth2 implementation with PKCE (Proof Key for Code Exchange) support in the Musify backend. PKCE is a security extension to OAuth2 designed specifically for public clients (mobile apps, SPAs) that cannot securely store client secrets.

## Why PKCE?

PKCE protects against:
- Authorization code interception attacks
- Man-in-the-middle attacks on the authorization code exchange
- Malicious apps on the same device intercepting the authorization code

## Implementation Details

### 1. Client Registration

Mobile apps should register as public clients:

```bash
POST /oauth2/register
Content-Type: application/json

{
  "client_name": "Musify iOS App",
  "redirect_uris": ["com.musify.ios://oauth/callback"],
  "token_endpoint_auth_method": "none",
  "grant_types": ["authorization_code"],
  "response_types": ["code"]
}
```

Response:
```json
{
  "client_id": "abc123-def456-...",
  "client_secret": null,
  "redirect_uris": ["com.musify.ios://oauth/callback"],
  "token_endpoint_auth_method": "none"
}
```

### 2. Authorization Flow with PKCE

#### Step 1: Generate Code Verifier and Challenge

Mobile app generates:
```kotlin
// Code Verifier: Random string 43-128 characters
val codeVerifier = generateRandomString(128)

// Code Challenge: SHA256(codeVerifier) encoded as base64url
val codeChallenge = sha256(codeVerifier).toBase64Url()
```

#### Step 2: Authorization Request

```
GET /oauth2/authorize?
  client_id=abc123-def456&
  redirect_uri=com.musify.ios://oauth/callback&
  response_type=code&
  scope=openid profile email&
  state=xyz789&
  code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&
  code_challenge_method=S256
```

User authenticates and approves. Server redirects to:
```
com.musify.ios://oauth/callback?code=AUTH_CODE&state=xyz789
```

#### Step 3: Token Exchange

```bash
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&
code=AUTH_CODE&
client_id=abc123-def456&
redirect_uri=com.musify.ios://oauth/callback&
code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
```

Response:
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
  "scope": "openid profile email"
}
```

### 3. Token Refresh

```bash
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token&
refresh_token=eyJhbGciOiJIUzI1NiIs...&
client_id=abc123-def456
```

### 4. Token Revocation

```bash
POST /oauth2/revoke
Content-Type: application/x-www-form-urlencoded

token=ACCESS_OR_REFRESH_TOKEN&
token_type_hint=access_token
```

## Mobile App Implementation

### iOS (Swift)

```swift
import CryptoKit
import AuthenticationServices

class OAuth2PKCEManager {
    private var codeVerifier: String?
    
    func startAuthorization() {
        // Generate code verifier
        codeVerifier = generateCodeVerifier()
        
        // Generate code challenge
        let codeChallenge = generateCodeChallenge(from: codeVerifier!)
        
        // Build authorization URL
        var components = URLComponents(string: "https://api.musify.com/oauth2/authorize")!
        components.queryItems = [
            URLQueryItem(name: "client_id", value: clientId),
            URLQueryItem(name: "redirect_uri", value: redirectUri),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: "openid profile email"),
            URLQueryItem(name: "state", value: generateState()),
            URLQueryItem(name: "code_challenge", value: codeChallenge),
            URLQueryItem(name: "code_challenge_method", value: "S256")
        ]
        
        // Open in ASWebAuthenticationSession
        let session = ASWebAuthenticationSession(
            url: components.url!,
            callbackURLScheme: "com.musify.ios"
        ) { callbackURL, error in
            // Handle callback
        }
        session.start()
    }
    
    private func generateCodeVerifier() -> String {
        var buffer = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, buffer.count, &buffer)
        return Data(buffer).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
    
    private func generateCodeChallenge(from verifier: String) -> String {
        let data = verifier.data(using: .utf8)!
        let hash = SHA256.hash(data: data)
        return Data(hash).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
```

### Android (Kotlin)

```kotlin
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class OAuth2PKCEManager(private val activity: Activity) {
    private var codeVerifier: String? = null
    
    fun startAuthorization() {
        // Generate code verifier
        codeVerifier = generateCodeVerifier()
        
        // Generate code challenge
        val codeChallenge = generateCodeChallenge(codeVerifier!!)
        
        // Build authorization URL
        val authUri = Uri.parse("https://api.musify.com/oauth2/authorize")
            .buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "openid profile email")
            .appendQueryParameter("state", generateState())
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
        
        // Open in Custom Tab
        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(activity, authUri)
    }
    
    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
    
    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(bytes, 0, bytes.size)
        val digest = messageDigest.digest()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
```

## Security Considerations

1. **Code Verifier Requirements**:
   - Must be cryptographically random
   - Length: 43-128 characters
   - Characters: [A-Z], [a-z], [0-9], "-", ".", "_", "~"

2. **Code Challenge Methods**:
   - `S256` (recommended): SHA256(code_verifier)
   - `plain` (not recommended): code_verifier as-is

3. **Authorization Code**:
   - Single use only
   - Expires in 10 minutes
   - Bound to client_id and redirect_uri

4. **Best Practices**:
   - Always use HTTPS
   - Validate redirect_uri exactly
   - Use state parameter to prevent CSRF
   - Store tokens securely on device (Keychain/Keystore)

## Testing

### Generate Test PKCE Values

```bash
# Generate code verifier
CODE_VERIFIER=$(openssl rand -base64 32 | tr -d "=+/" | cut -c 1-43)

# Generate code challenge
CODE_CHALLENGE=$(echo -n $CODE_VERIFIER | openssl dgst -sha256 -binary | base64 | tr -d "=+/" | tr -- '+/' '-_')

echo "Code Verifier: $CODE_VERIFIER"
echo "Code Challenge: $CODE_CHALLENGE"
```

### Test Authorization Flow

```bash
# Step 1: Get authorization code (manual in browser)
# Step 2: Exchange for token
curl -X POST https://api.musify.com/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=YOUR_AUTH_CODE" \
  -d "client_id=YOUR_CLIENT_ID" \
  -d "redirect_uri=YOUR_REDIRECT_URI" \
  -d "code_verifier=$CODE_VERIFIER"
```

## Troubleshooting

### Common Errors

1. **invalid_grant**: Code verifier doesn't match challenge
   - Ensure SHA256 is calculated correctly
   - Check base64url encoding (no padding, correct substitutions)

2. **invalid_request**: Missing PKCE parameters
   - Public clients must use PKCE
   - Include both code_challenge and code_challenge_method

3. **invalid_client**: Client not registered
   - Register app first via /oauth2/register
   - Use correct client_id

## References

- [RFC 7636 - Proof Key for Code Exchange](https://tools.ietf.org/html/rfc7636)
- [OAuth 2.0 for Mobile Apps](https://www.oauth.com/oauth2-servers/pkce/)
- [AppAuth Libraries](https://appauth.io/) - Recommended OAuth2/PKCE libraries