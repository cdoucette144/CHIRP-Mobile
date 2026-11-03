# Development APK signing

The repository development-signing.p12 uses the public password android and alias androiddebugkey. This is an intentionally shared development identity, never a production or store signing key. Keep production keys private and separate. Retaining this file makes development builds update-compatible. Transition from older randomly signed APKs requires a one-time data migration/reinstall.
