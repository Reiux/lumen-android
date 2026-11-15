# Translating Lumen

Thank you for helping make Lumen accessible to people around the world!

Lumen uses standard Android string resources for localization. You can contribute translations either online via **Hosted Weblate** (recommended, no coding required) or directly through a **GitHub Pull Request**.

---

## 1. Translate via Weblate (Recommended)

You can translate Lumen directly in your web browser without setting up Android Studio or using Git:

[![Translation status](https://hosted.weblate.org/widgets/lumen-android/-/app/svg-badge.svg)](https://hosted.weblate.org/engage/lumen-android/)

👉 **[Translate Lumen on Hosted Weblate](https://hosted.weblate.org/engage/lumen-android/)**

- Weblate automatically checks that placeholders like `%1$s` and XML tags stay intact.
- Translated strings are automatically synced to our GitHub repository.

---

## 2. Translate via GitHub Pull Request

If you prefer editing files directly, you can submit a PR:

### Base Strings
The English base strings are located in:
```
app/src/main/res/values/strings.xml
```

### Language Resource Folders
Inside `app/src/main/res/`, create or update the folder for your locale named `values-<language_code>/`:
- Bahasa Indonesia: `app/src/main/res/values-in/strings.xml`
- Japanese: `app/src/main/res/values-ja/strings.xml`
- Spanish: `app/src/main/res/values-es/strings.xml`
- German: `app/src/main/res/values-de/strings.xml`
- Portuguese (Brazil): `app/src/main/res/values-pt-rBR/strings.xml`

### Register in `locales_config.xml`
Add your language's BCP-47 tag in `app/src/main/res/xml/locales_config.xml` so Android 13+ per-app language settings can recognize it:

```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en"/>
    <locale android:name="id"/>
    <locale android:name="ja"/> <!-- Add your language here -->
</locale-config>
```

---

## Guidelines & Best Practices

1. **Keep String IDs Unchanged**: Do not alter `name="..."`. Only translate the content between `<string name="...">` and `</string>`.
2. **Preserve Format Specifiers**: Keep placeholders like `%1$s`, `%1$d`, `%,d` unchanged in the text. For example:
   ```xml
   <!-- English -->
   <string name="items_remaining">%1$d items remaining</string>
   <!-- Indonesian -->
   <string name="items_remaining">%1$d item tersisa</string>
   ```
3. **Escape Characters**:
   - Escape single quotes with a backslash: `\'` (e.g. `Google\'s`).
   - Use `&amp;` instead of raw `&`.
4. **Keep Brand Names Intact**: Do not translate "Lumen", "Google Photos", "Google One", "GitHub", or system names like "Android".
5. **Tone**: Use clean, friendly, natural phrasing matching standard mobile operating system terminology.

---

## Submitting Your Translation

1. Fork the repository.
2. Create a branch from `translations` (e.g., `translate/japanese`).
3. Commit your changes:
   ```bash
   git commit -m "i18n: add Japanese translation"
   ```
4. Open a Pull Request targeting the **`translations`** branch.
