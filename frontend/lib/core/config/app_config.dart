class AppConfig {
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: '',
  );

  static String get resolvedApiBaseUrl {
    if (apiBaseUrl.isNotEmpty) return apiBaseUrl;
    return '';
  }
}