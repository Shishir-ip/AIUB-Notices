# Add project specific ProGuard rules here.
# Code shrinking is disabled for this project (isMinifyEnabled = false), so these
# rules exist for anyone who later enables R8.

# Jsoup parses HTML via reflection-free APIs; keep its model classes intact.
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
