# Keep enum value helpers for stable diagnostics output.
-keepclassmembers enum com.example.btkeyboard.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
