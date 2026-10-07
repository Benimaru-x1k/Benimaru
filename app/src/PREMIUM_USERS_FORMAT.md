# Benimaru Premium GitHub license format

The app can continue using GitHub as the public source of truth. Do **not** keep plaintext passwords in `users.json`.

Each user record should look like:

```json
{
  "username": "example_user",
  "salt": "BASE64_16_BYTE_RANDOM_SALT",
  "passwordHash": "BASE64_PBKDF2_DERIVED_KEY",
  "devices": [
    "ANDROID_ID_1"
  ]
}
```

The Android app generates the `salt` and `passwordHash` locally and the **Copy Secure Registration** button copies a ready-to-paste JSON record. The plaintext password is never put in the PayPal URL.

Recommended migration steps:

1. Remove old `password` fields from `users.json`.
2. Open Premium Login in the app, enter the account username/password, and tap **Copy Secure Registration**.
3. Replace the old user object in GitHub with the copied object.
4. Keep at most two authorized device IDs per account.

Security limitation: because GitHub is intentionally public and the app verifies the password client-side, a determined attacker can download the public hash and attempt offline password guesses. Using a long, unique password makes this substantially harder. The app also uses Android Keystore to protect its local premium-activation state from simple SharedPreferences edits.
