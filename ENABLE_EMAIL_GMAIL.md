# Quick Gmail Setup for Email Verification

## Steps:

1. **Enable 2-Factor Authentication** (if not already enabled)
   - Go to https://myaccount.google.com/security
   - Click "2-Step Verification"
   - Follow the setup process

2. **Generate App Password**
   - Go to https://myaccount.google.com/apppasswords
   - Enter your Google password
   - Select "Mail" from dropdown
   - Click "Generate"
   - Copy the 16-character password (spaces don't matter)

3. **Update .env file**
   ```bash
   # Edit the .env file
   nano /home/robert/musify-backend/.env
   ```

   Change these lines:
   ```env
   EMAIL_ENABLED=true
   SMTP_HOST=smtp.gmail.com
   SMTP_PORT=587
   SMTP_USERNAME=your-gmail@gmail.com
   SMTP_PASSWORD=xxxx xxxx xxxx xxxx
   SMTP_USE_TLS=true
   ```

4. **Restart Backend**
   ```bash
   cd /home/robert/musify-backend
   ./gradlew run
   ```

5. **Test**
   - Register new user in app
   - Check your Gmail inbox
   - Click verification link

## Alternative: Test Without Email

Keep `EMAIL_ENABLED=false` and check console:
```bash
# Watch backend logs
tail -f run_output*.log | grep -A5 "verification"
```

The verification URL will appear in the console.