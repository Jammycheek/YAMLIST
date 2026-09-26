# SnakeYAML uses reflection over its config objects; keep them.
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**
