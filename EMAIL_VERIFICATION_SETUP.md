# Email Verification Setup Guide

## ✅ Backend Implementation Status

The backend already has a complete email verification system:

1. **Registration** - Generates verification token and sends email
2. **Email Service** - Supports SMTP and SendGrid
3. **Verification Endpoint** - `/api/auth/verify-email?token=xxx`
4. **Resend Endpoint** - `/api/auth/resend-verification`
5. **Email Templates** - Professional HTML templates

## 🚀 Quick Setup for Development

### Option 1: Using Gmail (Recommended for Testing)

1. **Enable 2-Factor Authentication on your Gmail**
   - Go to https://myaccount.google.com/security
   - Enable 2-Step Verification

2. **Generate App Password**
   - Go to https://myaccount.google.com/apppasswords
   - Select "Mail" and generate password
   - Copy the 16-character password

3. **Update .env file**
   ```env
   # Email Configuration
   EMAIL_ENABLED=true
   EMAIL_FROM_ADDRESS=your-email@gmail.com
   EMAIL_FROM_NAME=Musify

   # SMTP Configuration
   SMTP_HOST=smtp.gmail.com
   SMTP_PORT=587
   SMTP_USERNAME=your-email@gmail.com
   SMTP_PASSWORD=your-app-password-here
   SMTP_USE_TLS=true
   ```

### Option 2: Using Mailtrap (Development Email Testing)

1. **Sign up for free at** https://mailtrap.io
2. **Get SMTP credentials from inbox settings**
3. **Update .env file**
   ```env
   EMAIL_ENABLED=true
   EMAIL_FROM_ADDRESS=noreply@musify.local
   EMAIL_FROM_NAME=Musify Dev

   SMTP_HOST=smtp.mailtrap.io
   SMTP_PORT=2525
   SMTP_USERNAME=your-mailtrap-username
   SMTP_PASSWORD=your-mailtrap-password
   SMTP_USE_TLS=true
   ```

### Option 3: Console Output (No Email Service)

For development without email service, emails are logged to console:
```env
EMAIL_ENABLED=false
```

## 🔧 How Email Verification Works

### Registration Flow:
1. User registers with email/username/password
2. Backend creates user with `emailVerified=false`
3. Generates unique verification token
4. Sends email with verification link
5. User account is created but limited until verified

### Verification Flow:
1. User clicks link in email
2. Backend validates token
3. Updates `emailVerified=true`
4. Clears verification token
5. User can now access all features

### Resend Flow:
1. User requests new verification email
2. Backend generates new token
3. Sends new email
4. Old token is invalidated

## 📱 Frontend Implementation Needed

The Android app needs to handle email verification UI:

### 1. **After Registration**
```kotlin
// Show success message
"Account created! Check your email to verify your account."

// Navigate to verification pending screen
navController.navigate("verification-pending")
```

### 2. **Verification Pending Screen**
- Show message about checking email
- Resend verification button
- Open email app button
- Continue to login button

### 3. **Handle Verification Deep Link**
- Register URL scheme: `musify://verify-email?token=xxx`
- Extract token and call API
- Show success/error message
- Navigate to login

### 4. **Login Screen Updates**
- Check if user is verified after login
- Show banner if not verified
- Offer to resend verification

## 🧪 Testing Email Verification

### 1. **Test Registration**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "username": "testuser",
    "password": "password123",
    "displayName": "Test User"
  }'
```

### 2. **Check Console/Email**
- If EMAIL_ENABLED=false: Check console for verification URL
- If EMAIL_ENABLED=true: Check email inbox

### 3. **Verify Email**
```bash
# Copy token from email/console
curl http://localhost:8080/api/auth/verify-email?token=YOUR_TOKEN_HERE
```

### 4. **Resend Verification**
```bash
curl -X POST http://localhost:8080/api/auth/resend-verification \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com"}'
```

## 🚨 Important Security Notes

1. **Verification tokens expire** after 24 hours
2. **Tokens are single-use** - cleared after verification
3. **Rate limiting** on resend endpoint (max 3 per hour)
4. **Unverified users** have limited access

## 🎯 Next Steps

1. Enable email in .env file
2. Test registration flow
3. Implement frontend UI for verification
4. Add deep link handling in Android app
5. Test complete flow end-to-end